package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.response.student.*;
import com.inteliroadmap.backend.domain.entity.*;
import com.inteliroadmap.backend.domain.enums.RoadmapStepStatus;
import com.inteliroadmap.backend.mappers.StudentDashboardMapper;
import com.inteliroadmap.backend.repositories.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentDashboardServiceImpl {

    private static final int AI_TEXT_LIMIT = 80;

    private final UserRepository userRepository;
    private final SkillNodeRepository skillNodeRepository;
    private final StudentProgressRepository studentProgressRepository;
    private final CareerRequiredSkillRepository careerRequiredSkillRepository;
    private final FeedbackRepository feedbackRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SkillTrendRepository skillTrendRepository;
    private final StudentDashboardMapper studentDashboardMapper;
    private final AuthenticatedStudentServiceImpl authenticatedStudentService;
    private final StudentServiceImpl studentService;

    /**
     * Get roadmap progress for the current student dashboard.
     *
     * @return roadmap progress response with ordered steps and nullable AI tip
     */
    @Transactional
    public DashboardRoadmapProgressResponse getRoadmapProgress() {
        log.info("Student Dashboard Module: Fetching roadmap progress");

        Student student = getCurrentStudent();
        if (student == null || student.getCareerRole() == null) {
            return DashboardRoadmapProgressResponse.builder().build();
        }

        UUID careerId = student.getCareerRole().getCareerId();
        List<SkillNode> nodes = skillNodeRepository
                .findByCareerRole_CareerIdOrderByLevelAscNodeNameAsc(careerId);

        if (nodes.isEmpty()) {
            return DashboardRoadmapProgressResponse.builder().build();
        }

        List<UUID> nodeIds = nodes.stream()
                .map(SkillNode::getNodeId)
                .toList();
        List<StudentProgress> progresses = studentProgressRepository
                .findByStudent_UserIdAndSkillNode_NodeIdIn(student.getUserId(), nodeIds);
        Map<UUID, StudentProgress> progressByNodeId = mapProgressByNodeId(progresses);

        boolean currentNodeAssigned = false;
        List<RoadmapStepResponse> steps = new ArrayList<>();
        for (SkillNode node : nodes) {
            RoadmapStepStatus status = mapRoadmapStepStatus(node, progressByNodeId, currentNodeAssigned);
            if (status == RoadmapStepStatus.IN_PROGRESS) {
                currentNodeAssigned = true;
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
    public List<SkillGapItemResponse> getSkillGaps() {
        log.info("Student Dashboard Module: Fetching skill gaps");

        Student student = getCurrentStudent();
        if (student == null || student.getCareerRole() == null) {
            return List.of();
        }

        List<CareerRequiredSkill> missingSkills = studentService.findMissingRequiredSkills(student);
        return missingSkills.stream()
                .map(req -> studentDashboardMapper.toSkillGapItemResponse(req, studentService.calculateSkillProgress(student, req.getSkill())))
                .toList();
    }

    /**
     * Get latest mentor feedback for the current user.
     *
     * @return latest feedback list ordered by creation time descending
     */
    @Transactional
    public List<MentorFeedbackItemResponse> getMentorFeedback() {
        log.info("Student Dashboard Module: Fetching mentor feedback");

        User user = getCurrentUser();
        List<Feedback> feedbackList = feedbackRepository
                .findTop5ByReceiver_UserIdOrderByCreateAtDesc(user.getUserId());

        return feedbackList.stream()
                .map(studentDashboardMapper::toMentorFeedbackItemResponse)
                .toList();
    }

    /**
     * Get recent AI chat history for the current user.
     *
     * @return list of chat history preview items
     */
    @Transactional
    public List<AiHistoryItemResponse> getAiHistory() {
        log.info("Student Dashboard Module: Fetching AI chat history");

        User user = getCurrentUser();
        List<ChatSession> sessions = chatSessionRepository
                .findByUser_UserIdOrderByCreateAtDesc(user.getUserId());
        if (sessions.isEmpty()) {
            return List.of();
        }

        List<ChatMessage> messages = chatMessageRepository.findByChatSessionInOrderByCreateAtAsc(sessions);
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
    public MarketDemandResponse getMarketDemand() {
        log.info("Student Dashboard Module: Fetching market demand");

        Student student = getCurrentStudent();
        if (student == null || student.getCareerRole() == null) {
            return MarketDemandResponse.builder().build();
        }

        CareerRole careerRole = student.getCareerRole();
        List<CareerRequiredSkill> requiredSkills = careerRequiredSkillRepository
                .findByCareerRole_CareerId(careerRole.getCareerId());
        List<UUID> skillIds = requiredSkills.stream()
                .map(CareerRequiredSkill::getSkill)
                .filter(Objects::nonNull)
                .map(Skill::getSkillId)
                .toList();

        if (skillIds.isEmpty()) {
            return studentDashboardMapper.toEmptyMarketDemandResponse(careerRole);
        }

        List<SkillTrend> trends = skillTrendRepository.findBySkill_SkillIdInOrderByWeekStackAsc(skillIds);
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
    public List<RecommendationItemResponse> getRecommendations() {
        log.info("Student Dashboard Module: Fetching recommendations");

        Student student = getCurrentStudent();
        if (student == null || student.getCareerRole() == null) {
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

    private Student getCurrentStudent() {
        return authenticatedStudentService.getOrCreateStudent();
    }

    private User getCurrentUser() {
        Student student = authenticatedStudentService.getOrCreateStudent();
        return userRepository.findByUserId(student.getUserId());
    }

    private Map<UUID, StudentProgress> mapProgressByNodeId(List<StudentProgress> progresses) {
        Map<UUID, StudentProgress> progressByNodeId = new HashMap<>();
        for (StudentProgress progress : progresses) {
            if (progress.getSkillNode() != null) {
                progressByNodeId.put(progress.getSkillNode().getNodeId(), progress);
            }
        }
        return progressByNodeId;
    }

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

    private String shortenText(String content) {
        if (content == null) {
            return null;
        }
        if (content.length() <= AI_TEXT_LIMIT) {
            return content;
        }
        return content.substring(0, AI_TEXT_LIMIT);
    }

    private Map<LocalDate, Integer> sumJobsByWeek(List<SkillTrend> trends) {
        Map<LocalDate, Integer> jobsByWeek = new TreeMap<>();
        for (SkillTrend trend : trends) {
            if (trend.getWeekStack() == null || trend.getJobsNeeded() == null) {
                continue;
            }

            Integer currentValue = jobsByWeek.get(trend.getWeekStack());
            if (currentValue == null) {
                currentValue = 0;
            }
            jobsByWeek.put(trend.getWeekStack(), currentValue + trend.getJobsNeeded());
        }
        return jobsByWeek;
    }
}
