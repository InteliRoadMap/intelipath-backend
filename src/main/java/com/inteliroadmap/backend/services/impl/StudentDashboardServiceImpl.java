package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.response.student.AiHistoryItemResponse;
import com.inteliroadmap.backend.domain.dto.response.student.DashboardRoadmapProgressResponse;
import com.inteliroadmap.backend.domain.dto.response.student.MarketDemandResponse;
import com.inteliroadmap.backend.domain.dto.response.student.MentorFeedbackItemResponse;
import com.inteliroadmap.backend.domain.dto.response.student.RecommendationItemResponse;
import com.inteliroadmap.backend.domain.dto.response.student.RoadmapStepResponse;
import com.inteliroadmap.backend.domain.dto.response.student.SkillGapItemResponse;
import com.inteliroadmap.backend.domain.entity.CareerRequiredSkill;
import com.inteliroadmap.backend.domain.entity.CareerRole;
import com.inteliroadmap.backend.domain.entity.ChatMessage;
import com.inteliroadmap.backend.domain.entity.ChatSession;
import com.inteliroadmap.backend.domain.entity.Feedback;
import com.inteliroadmap.backend.domain.enums.FeedbackStatus;
import com.inteliroadmap.backend.domain.entity.SkillNode;
import com.inteliroadmap.backend.domain.entity.SkillTrend;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.StudentProgress;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.domain.enums.RoadmapStepStatus;
import com.inteliroadmap.backend.mappers.StudentDashboardMapper;
import com.inteliroadmap.backend.repositories.CareerRequiredSkillRepository;
import com.inteliroadmap.backend.repositories.CareerRoleRepository;
import com.inteliroadmap.backend.repositories.ChatMessageRepository;
import com.inteliroadmap.backend.repositories.ChatSessionRepository;
import com.inteliroadmap.backend.repositories.FeedbackRepository;
import com.inteliroadmap.backend.repositories.SkillNodeRepository;
import com.inteliroadmap.backend.repositories.SkillRepository;
import com.inteliroadmap.backend.repositories.SkillTrendRepository;
import com.inteliroadmap.backend.repositories.StudentProgressRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.services.AuthenticatedStudentService;
import com.inteliroadmap.backend.services.StudentDashboardService;
import com.inteliroadmap.backend.services.StudentService;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Implementation of the {@link StudentDashboardService}.
 * Provides aggregation of various metrics and insights for the student dashboard.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StudentDashboardServiceImpl implements StudentDashboardService {

    private static final int AI_TEXT_LIMIT = 80;

    /** Axis label for a week, named by the Monday it starts on. */
    private static final DateTimeFormatter WEEK_LABEL = DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH);

    private final UserRepository userRepository;
    private final SkillRepository skillRepository;
    private final SkillNodeRepository skillNodeRepository;
    private final CareerRoleRepository careerRoleRepository;
    private final StudentProgressRepository studentProgressRepository;
    private final CareerRequiredSkillRepository careerRequiredSkillRepository;
    private final FeedbackRepository feedbackRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SkillTrendRepository skillTrendRepository;
    private final StudentDashboardMapper studentDashboardMapper;
    private final AuthenticatedStudentService authenticatedStudentService;
    private final StudentService studentService;

    /**
     * Get roadmap progress for the current student dashboard.
     *
     * @return roadmap progress response with ordered steps and nullable AI tip
     */
    @Transactional
    @Override
    public DashboardRoadmapProgressResponse getRoadmapProgress() {
        log.info("StudentDashboardServiceImpl: Fetching roadmap progress");

        // Fetch current authenticated student and check if they have selected a career
        Student student = getCurrentStudent();
        return getRoadmapProgress(student);
    }

    @Transactional
    @Override
    public DashboardRoadmapProgressResponse getRoadmapProgress(Student student) {
        if (student == null || student.getCareerRole() == null || student.getCareerRole().getCareerId() == null) {
            return DashboardRoadmapProgressResponse.builder().build();
        }

        // Retrieve ordered skill nodes associated with the student's chosen career
        UUID careerId = student.getCareerRole().getCareerId();
        // Walk order, not alphabetical order: nodeLevel is 0 on most of the tree, so
        // the old finder's nodeName tie-break floated MongoDB operators ($all, $eq)
        // to the head of the student's action list.
        List<SkillNode> nodes = skillNodeRepository.findPublishedOrderedForCareer(careerId);

        if (nodes.isEmpty()) {
            return DashboardRoadmapProgressResponse.builder().build();
        }

        // Fetch all progress records for these specific nodes for the student
        List<UUID> nodeIds = nodes.stream()
                .map(SkillNode::getNodeId)
                .toList();
        List<StudentProgress> progresses = studentProgressRepository
                .findByStudent_UserIdAndSkillNode_NodeIdIn(student.getUserId(), nodeIds);
        
        // Map progress records by node ID for fast lookup during iteration
        Map<UUID, StudentProgress> progressByNodeId = mapProgressByNodeId(progresses);

        // Iterate through the roadmap nodes to determine the status of each step
        boolean currentNodeAssigned = false;
        List<RoadmapStepResponse> steps = new ArrayList<>();
        for (SkillNode node : nodes) {
            // Determine if a node is COMPLETED, IN_PROGRESS, or LOCKED
            RoadmapStepStatus status = mapRoadmapStepStatus(node, progressByNodeId, currentNodeAssigned);
            if (status == RoadmapStepStatus.IN_PROGRESS) {
                currentNodeAssigned = true; // Mark that we've found the current active step
            }

            steps.add(studentDashboardMapper.toRoadmapStepResponse(node, status));
        }

        return studentDashboardMapper.toRoadmapProgressResponse(steps);
    }

    /**
     * Get missing required skills for the current student's career.
     *
     * @return list of skill gap items
     */
    @Transactional
    @Override
    public List<SkillGapItemResponse> getSkillGaps() {
        log.info("StudentDashboardServiceImpl: Fetching skill gaps");

        Student student = getCurrentStudent();
        return getSkillGaps(student);
    }

    @Transactional
    @Override
    public List<SkillGapItemResponse> getSkillGaps(Student student) {
        if (student == null || student.getCareerRole() == null || student.getCareerRole().getCareerId() == null) {
            return List.of();
        }

        List<CareerRequiredSkill> missingSkills = studentService.findMissingRequiredSkills(student);
        return missingSkills.stream()
                .map(req -> studentDashboardMapper.toSkillGapItemResponse(
                        req, studentService.calculateSkillProgress(
                                student,
                                req.getSkill().getSkillId()
                        )
                ))
                .toList();
    }

    /**
     * Get latest mentor feedback for the current user.
     *
     * @return latest feedback list ordered by creation time descending
     */
    @Transactional
    @Override
    public List<MentorFeedbackItemResponse> getMentorFeedback() {
        log.info("StudentDashboardServiceImpl: Fetching mentor feedback");

        User user = getCurrentUser();
        List<Feedback> feedbackList = feedbackRepository
                .findTop5ByReceiver_UserIdAndStatusNotOrderByCreatedAtDesc(
                        user.getUserId(), FeedbackStatus.DELETED);

        return feedbackList.stream()
                .map(studentDashboardMapper::toMentorFeedbackItemResponse)
                .toList();
    }

    /**
     * Loads a feedback item that belongs to the current student (as receiver),
     * or throws 404 so we never reveal or mutate someone else's feedback.
     */
    private Feedback getOwnedFeedback(UUID feedbackId) {
        User user = getCurrentUser();
        Feedback feedback = feedbackRepository.findByFeedbackId(feedbackId);
        if (feedback == null || feedback.getReceiver() == null
                || !user.getUserId().equals(feedback.getReceiver().getUserId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Feedback not found");
        }
        return feedback;
    }

    @Transactional
    @Override
    public void markFeedbackRead(UUID feedbackId) {
        Feedback feedback = getOwnedFeedback(feedbackId);
        if (feedback.getStatus() != FeedbackStatus.READ && feedback.getStatus() != FeedbackStatus.DELETED) {
            feedback.setStatus(FeedbackStatus.READ);
            feedbackRepository.save(feedback);
        }
    }

    @Transactional
    @Override
    public void dismissFeedback(UUID feedbackId) {
        Feedback feedback = getOwnedFeedback(feedbackId);
        if (feedback.getStatus() != FeedbackStatus.DELETED) {
            feedback.setStatus(FeedbackStatus.DELETED);
            feedbackRepository.save(feedback);
        }
    }

    /**
     * Get recent AI chat history for the current user.
     *
     * @return list of chat history preview items
     */
    @Transactional
    @Override
    public List<AiHistoryItemResponse> getAiHistory() {
        log.info("StudentDashboardServiceImpl: Fetching AI chat history");

        User user = getCurrentUser();
        List<ChatSession> sessions = chatSessionRepository
                .findByUser_UserIdOrderByCreatedAtDesc(user.getUserId());
        if (sessions.isEmpty()) {
            return List.of();
        }

        List<UUID> sessionIds = sessions.stream().map(ChatSession::getSessionId).toList();
        List<ChatMessage> messages = chatMessageRepository.findByChatSession_SessionIdInOrderByCreatedAtAsc(sessionIds);
        List<AiHistoryItemResponse> historyItems = new ArrayList<>();
        for (ChatSession session : sessions) {
            ChatMessage message = selectDashboardMessage(session, messages);
            if (message != null) {
                String content = shortenText(message.getContent());
                historyItems.add(studentDashboardMapper.toAiHistoryItemResponse(session, message, content));
            }
        }

        return historyItems;
    }

    /**
     * Get market demand data for the current student's career.
     *
     * @return market demand response or null when the student has no career
     */
    @Transactional
    @Override
    public MarketDemandResponse getMarketDemand() {
        log.info("StudentDashboardServiceImpl: Fetching market demand");

        Student student = getCurrentStudent();
        if (student == null || student.getCareerRole() == null || student.getCareerRole().getCareerId() == null) {
            return MarketDemandResponse.builder().build();
        }

        CareerRole careerRole = careerRoleRepository.findByCareerId(student.getCareerRole().getCareerId());
        List<CareerRequiredSkill> requiredSkills = careerRequiredSkillRepository
                .findByCareerRole_CareerId(careerRole.getCareerId());
        List<UUID> skillIds = requiredSkills.stream()
                .map(s -> s.getSkill().getSkillId())
                .filter(Objects::nonNull)
                .toList();

        if (skillIds.isEmpty()) {
            return studentDashboardMapper.toEmptyMarketDemandResponse(careerRole);
        }

        List<SkillTrend> trends = skillTrendRepository.findBySkill_SkillIdInOrderByWeekStampAsc(skillIds);
        Map<LocalDate, Integer> jobsByWeek = sumJobsByWeek(trends);
        if (jobsByWeek.size() < 2) {
            return studentDashboardMapper.toEmptyMarketDemandResponse(careerRole);
        }

        List<LocalDate> weeks = new ArrayList<>(jobsByWeek.keySet());
        // TreeMap keeps these ascending, so the last two entries are the two most
        // recent whole weeks — a week-on-week comparison, not Saturday against Sunday.
        LocalDate previousWeek = weeks.get(weeks.size() - 2);
        LocalDate currentWeek = weeks.get(weeks.size() - 1);
        int previousJobs = jobsByWeek.get(previousWeek);
        int currentJobs = jobsByWeek.get(currentWeek);

        double growth = 0;
        if (previousJobs != 0) {
            growth = ((double) (currentJobs - previousJobs) / previousJobs) * 100;
        }

        return studentDashboardMapper.toMarketDemandResponse(
                careerRole,
                growth,
                new ArrayList<>(jobsByWeek.values()),
                weekLabels(jobsByWeek.keySet())
        );
    }

    /**
     * Generate recommendations from missing required skills.
     *
     * @return list of generated recommendations or an empty list
     */
    @Transactional
    @Override
    public List<RecommendationItemResponse> getRecommendations() {
        log.info("StudentDashboardServiceImpl: Fetching recommendations");

        Student student = getCurrentStudent();
        if (student == null || student.getCareerRole() == null || student.getCareerRole().getCareerId() == null) {
            return List.of();
        }

        List<CareerRequiredSkill> missingSkills = studentService.findMissingRequiredSkills(student);
        if (missingSkills.isEmpty()) {
            return List.of();
        }

        return missingSkills.stream()
                .sorted(Comparator.comparingInt(studentDashboardMapper::importanceRank))
                .map(studentDashboardMapper::toRecommendationItemResponse)
                .toList();
    }

    /**
     * Retrieves the currently authenticated student profile.
     *
     * @return the current {@link Student}
     */
    private Student getCurrentStudent() {
        return authenticatedStudentService.getOrCreateStudent();
    }

    /**
     * Retrieves the currently authenticated user profile based on the student session.
     *
     * @return the current {@link User}
     */
    private User getCurrentUser() {
        Student student = authenticatedStudentService.getOrCreateStudent();
        return userRepository.findByUserId(student.getUserId());
    }

    /**
     * Maps a list of student progress records into a map indexed by the node ID.
     *
     * @param progresses the list of {@link StudentProgress}
     * @return a map with the node ID as the key and the corresponding progress as the value
     */
    private Map<UUID, StudentProgress> mapProgressByNodeId(List<StudentProgress> progresses) {
        Map<UUID, StudentProgress> progressByNodeId = new HashMap<>();
        for (StudentProgress progress : progresses) {
            if (progress.getSkillNode().getNodeId() != null) {
                progressByNodeId.put(progress.getSkillNode().getNodeId(), progress);
            }
        }
        return progressByNodeId;
    }

    /**
     * Determines the status of a specific roadmap step for a given skill node.
     *
     * @param node the skill node in the roadmap
     * @param progressByNodeId map of student's progress indexed by node ID
     * @param currentNodeAssigned flag indicating if the current step is already assigned
     * @return the determined {@link RoadmapStepStatus}
     */
    private RoadmapStepStatus mapRoadmapStepStatus(
            SkillNode node,
            Map<UUID, StudentProgress> progressByNodeId,
            boolean currentNodeAssigned
    ) {
        StudentProgress progress = progressByNodeId.get(node.getNodeId());
        if (progress != null && "COMPLETED".equalsIgnoreCase(progress.getStatus().toString())) {
            return RoadmapStepStatus.COMPLETED;
        }

        if (!currentNodeAssigned) {
            return RoadmapStepStatus.IN_PROGRESS;
        }

        return RoadmapStepStatus.LOCKED;
    }

    /**
     * Selects the appropriate message to display on the dashboard preview for a chat session.
     * Prefers the first user message, otherwise falls back to the first available message.
     *
     * @param session the chat session
     * @param messages list of all messages in history
     * @return the selected {@link ChatMessage} for preview
     */
    private ChatMessage selectDashboardMessage(ChatSession session, List<ChatMessage> messages) {
        ChatMessage firstMessage = null;
        ChatMessage firstUserMessage = null;

        for (ChatMessage message : messages) {
            if (!message.getChatSession().getSessionId().equals(session.getSessionId())) {
                continue;
            }

            if (firstMessage == null) {
                firstMessage = message;
            }

            if (firstUserMessage == null && "USER".equalsIgnoreCase(message.getRole())) {
                firstUserMessage = message;
            }
        }

        if (firstUserMessage != null) {
            return firstUserMessage;
        }

        return firstMessage;
    }

    /**
     * Shortens the text content to a predefined length limit for preview purposes.
     *
     * @param content the original text content
     * @return the shortened text string
     */
    private String shortenText(String content) {
        if (content == null) {
            return null;
        }
        if (content.length() <= AI_TEXT_LIMIT) {
            return content;
        }
        return content.substring(0, AI_TEXT_LIMIT);
    }

    /**
     * Aggregates skill trend jobs into calendar weeks, keyed by each week's Monday.
     *
     * Despite its name {@code week_stamp} holds the <em>posting date</em> of the
     * advert — {@code SkillExtractionServiceImpl} writes one row per skill per day.
     * Grouping on it raw therefore produced a daily series, and the two things that
     * read this map both went wrong on it: the chart drew the ordinary weekend
     * trough as a sawtooth (Fri 205 jobs, Sat 21, Sun 12), and {@code growth}
     * compared the last two <em>days</em>, so every Sunday reported a collapse of
     * around -43% that was nothing but the weekend.
     *
     * The rows stay daily; only this rollup is weekly, so nothing else loses
     * resolution.
     *
     * @param trends the list of {@link SkillTrend} data
     * @return aggregated job count per week, ascending, Monday-keyed
     */
    private Map<LocalDate, Integer> sumJobsByWeek(List<SkillTrend> trends) {
        TreeMap<LocalDate, Integer> jobsByWeek = new TreeMap<>();
        LocalDate earliestDay = null;
        LocalDate latestDay = null;
        for (SkillTrend trend : trends) {
            if (trend.getWeekStamp() == null || trend.getJobsNeeded() == null) {
                continue;
            }

            LocalDate day = trend.getWeekStamp();
            if (earliestDay == null || day.isBefore(earliestDay)) {
                earliestDay = day;
            }
            if (latestDay == null || day.isAfter(latestDay)) {
                latestDay = day;
            }
            jobsByWeek.merge(day.with(DayOfWeek.MONDAY), trend.getJobsNeeded(), Integer::sum);
        }

        // Both ends of the crawl window land mid-week, and a part-week is not a small
        // week: the leading one held 75 jobs against ~800 for its whole neighbours,
        // which draws as a collapse that never happened. Trim whichever boundary the
        // data does not actually cover, but never trim away the last point standing.
        if (jobsByWeek.size() > 1 && earliestDay != null && earliestDay.isAfter(jobsByWeek.firstKey())) {
            jobsByWeek.remove(jobsByWeek.firstKey());
        }
        if (jobsByWeek.size() > 1 && latestDay != null && latestDay.isBefore(jobsByWeek.lastKey().plusDays(6))) {
            jobsByWeek.remove(jobsByWeek.lastKey());
        }
        return jobsByWeek;
    }

    /** Week labels matching {@link #sumJobsByWeek}'s values, e.g. "27 Jul". */
    private List<String> weekLabels(Collection<LocalDate> weekStarts) {
        return weekStarts.stream().map(WEEK_LABEL::format).toList();
    }
}
