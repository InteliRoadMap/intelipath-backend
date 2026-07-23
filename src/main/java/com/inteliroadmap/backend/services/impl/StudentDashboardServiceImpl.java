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

import java.time.LocalDate;
import java.util.ArrayList;
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
        List<SkillNode> nodes = skillNodeRepository
                .findByCareerRole_CareerIdOrderByNodeLevelAscNodeNameAsc(careerId);

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
                new ArrayList<>(jobsByWeek.values())
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
     * Aggregates skill trend jobs by their respective week stack dates.
     *
     * @param trends the list of {@link SkillTrend} data
     * @return a map containing the aggregated job count per week date
     */
    private Map<LocalDate, Integer> sumJobsByWeek(List<SkillTrend> trends) {
        Map<LocalDate, Integer> jobsByWeek = new TreeMap<>();
        for (SkillTrend trend : trends) {
            if (trend.getWeekStamp() == null || trend.getJobsNeeded() == null) {
                continue;
            }

            Integer currentValue = jobsByWeek.get(trend.getWeekStamp());
            if (currentValue == null) {
                currentValue = 0;
            }
            jobsByWeek.put(trend.getWeekStamp(), currentValue + trend.getJobsNeeded());
        }
        return jobsByWeek;
    }
}
