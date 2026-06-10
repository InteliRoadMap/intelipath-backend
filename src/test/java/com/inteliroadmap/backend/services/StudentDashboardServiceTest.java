package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.response.SkillResponse;
import com.inteliroadmap.backend.domain.dto.response.dashboard.*;
import com.inteliroadmap.backend.domain.entity.*;
import com.inteliroadmap.backend.domain.enums.RoadmapStepStatus;
import com.inteliroadmap.backend.helper.AuthenticatedStudentHelper;
import com.inteliroadmap.backend.mappers.SkillMapper;
import com.inteliroadmap.backend.mappers.StudentDashboardMapper;
import com.inteliroadmap.backend.repositories.*;
import com.inteliroadmap.backend.services.dashboard.StudentDashboardService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StudentDashboardServiceTest {

    private UserRepository userRepository;
    private StudentRepository studentRepository;
    private SkillNodeRepository skillNodeRepository;
    private StudentProgressRepository studentProgressRepository;
    private CareerRequiredSkillRepository careerRequiredSkillRepository;
    private StudentSkillRepository studentSkillRepository;
    private FeedbackRepository feedbackRepository;
    private ChatSessionRepository chatSessionRepository;
    private ChatMessageRepository chatMessageRepository;
    private SkillTrendRepository skillTrendRepository;
    private StudentDashboardService studentDashboardService;

    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        studentRepository = mock(StudentRepository.class);
        skillNodeRepository = mock(SkillNodeRepository.class);
        studentProgressRepository = mock(StudentProgressRepository.class);
        careerRequiredSkillRepository = mock(CareerRequiredSkillRepository.class);
        studentSkillRepository = mock(StudentSkillRepository.class);
        feedbackRepository = mock(FeedbackRepository.class);
        chatSessionRepository = mock(ChatSessionRepository.class);
        chatMessageRepository = mock(ChatMessageRepository.class);
        skillTrendRepository = mock(SkillTrendRepository.class);

        studentDashboardService = new StudentDashboardService(
                userRepository,
                studentRepository,
                skillNodeRepository,
                studentProgressRepository,
                careerRequiredSkillRepository,
                studentSkillRepository,
                feedbackRepository,
                chatSessionRepository,
                chatMessageRepository,
                skillTrendRepository,
                new SkillMapper(),
                new StudentDashboardMapper(),
                mock(AuthenticatedStudentHelper.class)
        );

        user = User.builder()
                .userId(UUID.randomUUID())
                .email("student@example.com")
                .fullName("Student One")
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getEmail(), null)
        );
        when(userRepository.findByEmail(user.getEmail())).thenReturn(user);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void roadmapProgressReturnsEmptyWhenStudentHasNoCareer() {
        Student student = student(null);
        when(studentRepository.findById(user.getUserId())).thenReturn(java.util.Optional.of(student));

        DashboardRoadmapProgressResponse response = studentDashboardService.getRoadmapProgress();

        assertNotNull(response.getSteps());
        assertTrue(response.getSteps().isEmpty());
        assertNull(response.getAiTip());
    }

    @Test
    void roadmapProgressMarksFirstIncompleteNodeAsCurrentWhenNoProgressExists() {
        CareerRole career = career();
        Student student = student(career);
        SkillNode firstNode = node(career, "Java Basics");
        SkillNode secondNode = node(career, "Spring Boot");
        when(studentRepository.findById(user.getUserId())).thenReturn(java.util.Optional.of(student));
        when(skillNodeRepository.findByCareerRole_CareerIdOrderByLevelAscNodeNameAsc(career.getCareerId()))
                .thenReturn(List.of(firstNode, secondNode));
        when(studentProgressRepository.findByStudent_UserIdAndSkillNode_NodeIdIn(
                student.getUserId(),
                List.of(firstNode.getNodeId(), secondNode.getNodeId())
        )).thenReturn(List.of());

        DashboardRoadmapProgressResponse response = studentDashboardService.getRoadmapProgress();

        assertEquals(RoadmapStepStatus.IN_PROGRESS, response.getSteps().get(0).getStatus());
        assertEquals(RoadmapStepStatus.LOCKED, response.getSteps().get(1).getStatus());
    }

    @Test
    void roadmapProgressMapsCompletedCurrentAndLockedStatuses() {
        CareerRole career = career();
        Student student = student(career);
        SkillNode firstNode = node(career, "Java Basics");
        SkillNode secondNode = node(career, "Spring Boot");
        SkillNode thirdNode = node(career, "Deployment");
        StudentProgress completedProgress = StudentProgress.builder()
                .student(student)
                .skillNode(firstNode)
                .status("COMPLETED")
                .build();
        when(studentRepository.findById(user.getUserId())).thenReturn(java.util.Optional.of(student));
        when(skillNodeRepository.findByCareerRole_CareerIdOrderByLevelAscNodeNameAsc(career.getCareerId()))
                .thenReturn(List.of(firstNode, secondNode, thirdNode));
        when(studentProgressRepository.findByStudent_UserIdAndSkillNode_NodeIdIn(
                student.getUserId(),
                List.of(firstNode.getNodeId(), secondNode.getNodeId(), thirdNode.getNodeId())
        )).thenReturn(List.of(completedProgress));

        DashboardRoadmapProgressResponse response = studentDashboardService.getRoadmapProgress();

        assertEquals(RoadmapStepStatus.COMPLETED, response.getSteps().get(0).getStatus());
        assertEquals(RoadmapStepStatus.IN_PROGRESS, response.getSteps().get(1).getStatus());
        assertEquals(RoadmapStepStatus.LOCKED, response.getSteps().get(2).getStatus());
    }

//    @Test
//    void skillGapsUseDatabaseValuesWithoutHardcodedDescription() {
//        CareerRole career = career();
//        Student student = student(career);
//        Skill java = skill("Java", "Backend", "Software Developer");
//        CareerRequiredSkill requiredSkill = requiredSkill(career, java, "HIGH");
//        when(studentRepository.findById(user.getUserId())).thenReturn(java.util.Optional.of(student));
//        when(careerRequiredSkillRepository.findByCareerRole_CareerId(career.getCareerId()))
//                .thenReturn(List.of(requiredSkill));
//        when(studentSkillRepository.findByStudent_UserId(student.getUserId())).thenReturn(List.of());
//
//        List<SkillGapItemResponse> response = studentDashboardService.getSkillGaps();
//
//        assertEquals(1, response.size());
//        assertEquals(java.getSkillId(), response.get(0).getId());
//        assertEquals("critical", response.get(0).getType());
//        assertEquals("Java", response.get(0).getTitle());
//        assertEquals("Backend", response.get(0).getDescription());
//        assertEquals("HIGH", response.get(0).getSeverity());
//    }
//
//    @Test
//    void compareCurrentStudentSkillsNeverReturnsNullListsWhenCareerIsMissing() {
//        Student student = student(null);
//        when(studentRepository.findById(user.getUserId())).thenReturn(java.util.Optional.of(student));
//        when(studentSkillRepository.findByStudent_UserId(student.getUserId())).thenReturn(List.of());
//
//        SkillResponse response = studentDashboardService.compareCurrentStudentSkills();
//
//        assertNotNull(response.getSelectedSkills());
//        assertNotNull(response.getSkills());
//        assertNotNull(response.getRequiredSkills());
//        assertNotNull(response.getMissingSkills());
//        assertTrue(response.getRequiredSkills().isEmpty());
//        assertTrue(response.getMissingSkills().isEmpty());
//    }
//
//    @Test
//    void mentorFeedbackReturnsEmptyListWhenNoFeedbackExists() {
//        when(feedbackRepository.findTop5ByReceiver_UserIdOrderByCreateAtDesc(user.getUserId()))
//                .thenReturn(List.of());
//
//        List<MentorFeedbackItemResponse> response = studentDashboardService.getMentorFeedback();
//
//        assertNotNull(response);
//        assertTrue(response.isEmpty());
//    }
//
//    @Test
//    void aiHistoryReturnsEmptyListWhenNoChatSessionsExist() {
//        when(chatSessionRepository.findByUser_UserIdOrderByCreateAtDesc(user.getUserId()))
//                .thenReturn(List.of());
//
//        List<AiHistoryItemResponse> response = studentDashboardService.getAiHistory();
//
//        assertNotNull(response);
//        assertTrue(response.isEmpty());
//    }
//
//    @Test
//    void marketDemandReturnsEmptyChartWhenTrendDataIsMissing() {
//        CareerRole career = career();
//        Student student = student(career);
//        Skill java = skill("Java", "Backend", "Software Developer");
//        when(studentRepository.findById(user.getUserId())).thenReturn(java.util.Optional.of(student));
//        when(careerRequiredSkillRepository.findByCareerRole_CareerId(career.getCareerId()))
//                .thenReturn(List.of(requiredSkill(career, java, "HIGH")));
//        when(skillTrendRepository.findBySkill_SkillIdInOrderByWeekStackAsc(List.of(java.getSkillId())))
//                .thenReturn(List.of());
//
//        MarketDemandResponse response = studentDashboardService.getMarketDemand();
//
//        assertNotNull(response);
//        assertEquals(0, response.getGrowth());
//        assertEquals(career.getCareerName(), response.getRole());
//        assertNotNull(response.getChart());
//        assertTrue(response.getChart().isEmpty());
//    }
//
//    @Test
//    void recommendationsReturnEmptyWhenThereAreNoMissingSkills() {
//        CareerRole career = career();
//        Student student = student(career);
//        Skill java = skill("Java", "Backend", "Software Developer");
//        CareerRequiredSkill requiredSkill = requiredSkill(career, java, "HIGH");
//        StudentSkill selectedSkill = StudentSkill.builder()
//                .student(student)
//                .skill(java)
//                .build();
//        when(studentRepository.findById(user.getUserId())).thenReturn(java.util.Optional.of(student));
//        when(careerRequiredSkillRepository.findByCareerRole_CareerId(career.getCareerId()))
//                .thenReturn(List.of(requiredSkill));
//        when(studentSkillRepository.findByStudent_UserId(student.getUserId()))
//                .thenReturn(List.of(selectedSkill));
//
//        List<RecommendationItemResponse> response = studentDashboardService.getRecommendations();
//
//        assertNotNull(response);
//        assertTrue(response.isEmpty());
//    }

    private Student student(CareerRole careerRole) {
        return Student.builder()
                .userId(user.getUserId())
                .careerRole(careerRole)
                .build();
    }

    private CareerRole career() {
        return CareerRole.builder()
                .careerId(UUID.randomUUID())
                .careerName("Backend Developer")
                .build();
    }

    private SkillNode node(CareerRole career, String name) {
        return SkillNode.builder()
                .nodeId(UUID.randomUUID())
                .careerRole(career)
                .nodeName(name)
                .build();
    }

    private Skill skill(String skillName, String category, String career) {
        return Skill.builder()
                .skillId(UUID.randomUUID())
                .skillName(skillName)
                .category(category)
                .career(career)
                .build();
    }

    private CareerRequiredSkill requiredSkill(CareerRole careerRole, Skill skill, String importanceLevel) {
        return CareerRequiredSkill.builder()
                .skillRequiredId(UUID.randomUUID())
                .careerRole(careerRole)
                .skill(skill)
                .importanceLevel(importanceLevel)
                .build();
    }
}
