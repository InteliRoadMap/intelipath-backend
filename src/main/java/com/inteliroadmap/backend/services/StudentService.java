package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.response.SkillResponse;
import com.inteliroadmap.backend.domain.dto.response.StudentResponse;
import com.inteliroadmap.backend.domain.dto.response.student.*;
import com.inteliroadmap.backend.domain.entity.*;
import com.inteliroadmap.backend.domain.enums.RoadmapStepStatus;
import com.inteliroadmap.backend.services.AuthenticatedStudentService;
import com.inteliroadmap.backend.mappers.SkillMapper;
import com.inteliroadmap.backend.mappers.StudentDashboardMapper;
import com.inteliroadmap.backend.mappers.StudentMapper;
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
public class StudentService {

    private static final int AI_TEXT_LIMIT = 80;

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final SkillNodeRepository skillNodeRepository;
    private final StudentProgressRepository studentProgressRepository;
    private final CareerRequiredSkillRepository careerRequiredSkillRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final FeedbackRepository feedbackRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SkillTrendRepository skillTrendRepository;
    private final SkillMapper skillMapper;
    private final StudentDashboardMapper studentDashboardMapper;
    private final StudentMapper studentMapper;
    private final CareerRoleRepository careerRoleRepository;
    private final AuthenticatedStudentService AuthenticatedStudentService;

    @Transactional
    public StudentResponse setupStudentProfile(com.inteliroadmap.backend.domain.dto.request.SetupStudentProfileRequest request) {
        log.info("Student Module: Setup Student Profile Request received");

        Student student = AuthenticatedStudentService.getOrCreateStudentForUpdate();
        User user = userRepository.findByUserId(student.getUserId());
        if (user == null) {
            throw new com.inteliroadmap.backend.exceptions.ResourceNotFoundException("User not found");
        }

        if (request.getUniversity() != null) student.setUniversity(request.getUniversity());
        if (request.getYearOfAdmission() != null && !request.getYearOfAdmission().trim().isEmpty()) {
            student.setYearOfAdmission(LocalDate.parse(request.getYearOfAdmission()));
        }

        if (request.getMajor() != null) {
            student.setMajor(request.getMajor());
        }

        if (request.getCareerId() != null) {
            CareerRole career = careerRoleRepository.findByCareerId(request.getCareerId());
            if (career == null) {
                log.warn("Career role was not found: {}", request.getCareerId());
                throw new com.inteliroadmap.backend.exceptions.ResourceNotFoundException("Career role not found");
            }
            student.setCareerRole(career);
        }

        boolean userChanged = false;
        if (request.getBio() != null) {
            user.setBio(request.getBio());
            userChanged = true;
        }
        if (request.getYob() != null && !request.getYob().trim().isEmpty()) {
            user.setYob(LocalDate.parse(request.getYob()));
            userChanged = true;
        }

        if (userChanged) {
            userRepository.save(user);
        }

        studentRepository.save(student);

        log.info("Student profile updated successfully for user: {}", user.getEmail());
        return studentMapper.toProfileResponse(student);
    }

    @Transactional
    public StudentResponse getStudentProfile() {
        log.info("Student profile retrieval request received");
        Student student = AuthenticatedStudentService.getRequiredStudent();
        return studentMapper.toProfileResponse(student);
    }

    @Transactional
    public StudentResponse updateTargetCareer(UUID careerId) {
        log.info("Student target career update request received. careerId: {}", careerId);
        Student student = AuthenticatedStudentService.getOrCreateStudentForUpdate();
        CareerRole career = careerRoleRepository.findByCareerId(careerId);
        if (career == null) {
            throw new com.inteliroadmap.backend.exceptions.ResourceNotFoundException("Career role not found");
        }
        student.setCareerRole(career);
        studentRepository.save(student);
        log.info("Student target career updated successfully. careerId: {}", careerId);
        return studentMapper.toProfileResponse(student);
    }

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

        List<CareerRequiredSkill> missingSkills = findMissingRequiredSkills(student);
        return missingSkills.stream()
                .map(studentDashboardMapper::toSkillGapItemResponse)
                .toList();
    }

    /**
     * Compare the current student's selected skills with required career skills.
     *
     * @return SkillResponse containing selected, required, and missing skills
     */
    @Transactional
    public SkillResponse compareCurrentStudentSkills() {
        log.info("Student Dashboard Module: Comparing selected skills with required skills");

        Student student = getCurrentStudent();
        if (student == null) {
            return SkillResponse.builder().build();
        }

        List<StudentSkill> selectedSkills = studentSkillRepository.findByStudent_UserId(student.getUserId());
        if (student.getCareerRole() == null) {
            return SkillResponse.builder()
                    .selectedSkills(skillMapper.toSelectedSkillResponses(selectedSkills))
                    .build();
        }

        List<CareerRequiredSkill> requiredSkills = careerRequiredSkillRepository
                .findByCareerRole_CareerId(student.getCareerRole().getCareerId());
        List<CareerRequiredSkill> missingRequiredSkills = filterMissingRequiredSkills(requiredSkills, selectedSkills);
        List<Skill> missingSkills = missingRequiredSkills.stream()
                .map(CareerRequiredSkill::getSkill)
                .toList();

        return SkillResponse.builder()
                .selectedSkills(skillMapper.toSelectedSkillResponses(selectedSkills))
                .requiredSkills(skillMapper.toRequiredSkillResponses(requiredSkills))
                .missingSkills(skillMapper.toSkillItemResponses(missingSkills))
                .build();
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

        List<CareerRequiredSkill> missingSkills = findMissingRequiredSkills(student);
        if (missingSkills.isEmpty()) {
            return List.of();
        }

        return missingSkills.stream()
                .sorted(Comparator.comparingInt(studentDashboardMapper::importanceRank))
                .map(studentDashboardMapper::toRecommendationItemResponse)
                .toList();
    }

    private Student getCurrentStudent() {
        return AuthenticatedStudentService.getOrCreateStudent();
    }

    private User getCurrentUser() {
        Student student = AuthenticatedStudentService.getOrCreateStudent();
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

    private List<CareerRequiredSkill> findMissingRequiredSkills(Student student) {
        List<CareerRequiredSkill> requiredSkills = careerRequiredSkillRepository
                .findByCareerRole_CareerId(student.getCareerRole().getCareerId());
        List<StudentSkill> selectedSkills = studentSkillRepository.findByStudent_UserId(student.getUserId());
        return filterMissingRequiredSkills(requiredSkills, selectedSkills);
    }

    private List<CareerRequiredSkill> filterMissingRequiredSkills(
            List<CareerRequiredSkill> requiredSkills,
            List<StudentSkill> selectedSkills
    ) {
        Set<UUID> selectedSkillIds = selectedSkills.stream()
                .map(StudentSkill::getSkill)
                .filter(Objects::nonNull)
                .map(Skill::getSkillId)
                .collect(java.util.stream.Collectors.toSet());

        return requiredSkills.stream()
                .filter(requiredSkill -> requiredSkill.getSkill() != null)
                .filter(requiredSkill -> !selectedSkillIds.contains(requiredSkill.getSkill().getSkillId()))
                .toList();
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
