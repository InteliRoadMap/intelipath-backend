package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.request.PortfolioConfigRequest;
import com.inteliroadmap.backend.domain.dto.request.PortfolioProjectRequest;
import com.inteliroadmap.backend.domain.dto.request.StudentEducationRequest;

import com.inteliroadmap.backend.domain.dto.request.PortfolioUpsertRequest;
import com.inteliroadmap.backend.domain.dto.request.RequestReviewRequest;
import com.inteliroadmap.backend.domain.dto.response.portfolio.PortfolioResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.PortfolioAboutDraftResponse;
import com.inteliroadmap.backend.domain.dto.internal.portfolio.PortfolioAboutSkillFact;
import com.inteliroadmap.backend.ai.analyzer.PortfolioAboutAiAnalyzer;
import com.inteliroadmap.backend.domain.entity.PortfolioConfig;
import com.inteliroadmap.backend.domain.entity.PortfolioProject;
import com.inteliroadmap.backend.domain.entity.PortfolioReviewRequest;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.StudentEducation;
import com.inteliroadmap.backend.domain.entity.StudentSkill;
import com.inteliroadmap.backend.domain.entity.StudentSkillEvidence;
import com.inteliroadmap.backend.domain.entity.CareerRequiredSkill;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.domain.enums.EvidenceStatus;
import com.inteliroadmap.backend.domain.enums.ReviewStatus;
import com.inteliroadmap.backend.domain.enums.UserRole;
import com.inteliroadmap.backend.exceptions.BadRequestException;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.services.AuthenticatedStudentService;
import com.inteliroadmap.backend.repositories.PortfolioConfigRepository;
import com.inteliroadmap.backend.repositories.PortfolioProjectRepository;
import com.inteliroadmap.backend.repositories.PortfolioReviewRequestRepository;
import com.inteliroadmap.backend.repositories.StudentEducationRepository;
import com.inteliroadmap.backend.repositories.StudentRepository;
import com.inteliroadmap.backend.repositories.StudentSkillEvidenceRepository;
import com.inteliroadmap.backend.repositories.StudentSkillRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.repositories.CareerRequiredSkillRepository;
import com.inteliroadmap.backend.repositories.RecruitmentSkillRepository;
import com.inteliroadmap.backend.mappers.PortfolioMapper;
import com.inteliroadmap.backend.services.PortfolioService;
import com.inteliroadmap.backend.services.RoadmapService;
import com.inteliroadmap.backend.services.StudentLevelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of the {@link PortfolioService} interface.
 * Handles operations related to the student's portfolio, including retrieval and updates
 * of portfolio configurations, projects, education, and skills.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioServiceImpl implements PortfolioService {

    private final AuthenticatedStudentService authenticatedStudentService;
    private final PortfolioMapper portfolioMapper;
    private final PortfolioConfigRepository portfolioConfigRepository;
    private final PortfolioProjectRepository portfolioProjectRepository;
    private final StudentEducationRepository studentEducationRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final StudentSkillEvidenceRepository studentSkillEvidenceRepository;
    private final PortfolioReviewRequestRepository portfolioReviewRequestRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final RoadmapService roadmapService;
    private final StudentLevelService studentLevelService;
    private final PortfolioAboutAiAnalyzer portfolioAboutAiAnalyzer;
    private final CareerRequiredSkillRepository careerRequiredSkillRepository;
    private final RecruitmentSkillRepository recruitmentSkillRepository;

    @Override
    @Transactional(readOnly = true)
    public PortfolioAboutDraftResponse generateAboutDraft() {
        Student student = authenticatedStudentService.getRequiredStudent();
        PortfolioResponse portfolio = getPortfolio();
        String career = portfolio.getLearningJourney() == null ? null
                : portfolio.getLearningJourney().getTargetCareerRole();
        String level = portfolio.getStudentLevel() == null ? null : portfolio.getStudentLevel().getLevel();
        List<PortfolioAboutSkillFact> facts = rankedSkillFacts(student);
        String primarySkills = facts.stream()
                .filter(fact -> fact.careerImportance() != null)
                .limit(12)
                .map(PortfolioAboutSkillFact::promptLine)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("(not provided)");
        String secondarySkills = facts.stream()
                .filter(fact -> fact.careerImportance() == null)
                .limit(12)
                .map(PortfolioAboutSkillFact::promptLine)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("(not provided)");
        String projects = portfolio.getProjects().stream()
                .limit(8)
                .map(project -> project.getProjectName() + ": " + project.getDescription())
                .reduce((left, right) -> left + " | " + right)
                .orElse("(not provided)");
        return portfolioAboutAiAnalyzer.draft(career, level, portfolio.getUserInfo().getBio(),
                primarySkills, secondarySkills, projects);
    }

    private List<PortfolioAboutSkillFact> rankedSkillFacts(Student student) {
        if (student.getCareerRole() == null) return List.of();
        UUID careerId = student.getCareerRole().getCareerId();
        Map<UUID, CareerRequiredSkill> requiredBySkill = new HashMap<>();
        for (CareerRequiredSkill required : careerRequiredSkillRepository.findByCareerRole_CareerId(careerId)) {
            requiredBySkill.put(required.getSkill().getSkillId(), required);
        }
        Map<UUID, Long> demandBySkill = new HashMap<>();
        for (Object[] row : recruitmentSkillRepository.demandByCareer()) {
            if (careerId.equals(row[0])) {
                demandBySkill.put((UUID) row[1], ((Number) row[3]).longValue());
            }
        }

        List<PortfolioAboutSkillFact> facts = new ArrayList<>();
        for (StudentSkill held : studentSkillRepository.findByStudent_UserId(student.getUserId())) {
            UUID skillId = held.getSkill().getSkillId();
            CareerRequiredSkill required = requiredBySkill.get(skillId);
            facts.add(new PortfolioAboutSkillFact(
                    held.getSkill().getSkillName(),
                    required == null ? null : required.getImportanceLevel(),
                    held.getProficiency(),
                    held.getVerifiedBy(),
                    demandBySkill.getOrDefault(skillId, 0L)));
        }
        facts.sort(Comparator
                .comparingInt((PortfolioAboutSkillFact fact) -> importanceRank(fact.careerImportance())).reversed()
                .thenComparing(PortfolioAboutSkillFact::verified, Comparator.reverseOrder())
                .thenComparing(Comparator.comparingLong(PortfolioAboutSkillFact::careerPostingCount).reversed())
                .thenComparing(fact -> fact.proficiency() == null ? 0 : fact.proficiency(), Comparator.reverseOrder())
                .thenComparing(PortfolioAboutSkillFact::skillName));
        return facts;
    }

    private int importanceRank(com.inteliroadmap.backend.domain.enums.ImportanceLevel importance) {
        if (importance == null) return 0;
        return switch (importance) {
            case HIGH -> 3;
            case AVG -> 2;
            case LOW -> 1;
        };
    }

    /**
     * Evidence that still stands behind the student's skills. REJECTED rows are left out:
     * a claim someone already turned down must not earn a verified badge on the portfolio.
     */
    private List<StudentSkillEvidence> liveEvidence(UUID userId) {
        return studentSkillEvidenceRepository.findByUserIdAndStatusIn(
                userId, List.of(EvidenceStatus.PENDING, EvidenceStatus.ACCEPTED));
    }

    /**
     * Retrieves the portfolio for the currently authenticated student.
     *
     * @return a {@link PortfolioResponse} containing the user's portfolio data
     * @throws ResourceNotFoundException if the associated user is not found
     */
    @Transactional(readOnly = true)
    @Override
    public PortfolioResponse getPortfolio() {
        // Retrieve the currently authenticated student
        Student student = authenticatedStudentService.getRequiredStudent();
        // Fetch the associated user entity using the student's user ID
        Optional<User> userOpt = userRepository.findById(student.getUserId());
        if (userOpt.isEmpty()) {
            log.error("PortfolioServiceImpl: User not found for ID: {}", student.getUserId());
            throw new ResourceNotFoundException("User not found");
        }
        User user = userOpt.get();

        // Fetch portfolio configuration, skills, projects, and education for the student
        PortfolioConfig config = portfolioConfigRepository.findByUser_UserId(student.getUserId());
        List<StudentSkill> skills = studentSkillRepository.findByStudent_UserId(student.getUserId());
        List<PortfolioProject> projects = portfolioProjectRepository.findByUser_UserId(user.getUserId());
        List<StudentEducation> education = studentEducationRepository.findByUser_UserId(student.getUserId());

        // Map the collected data into a PortfolioResponse object
        List<StudentSkillEvidence> evidence = liveEvidence(student.getUserId());

        return portfolioMapper.toPortfolioResponse(user, student, config, skills, projects, education, evidence,
                roadmapService.getStudentRoadmapForPortfolio(student),
                studentLevelService.levelOf(student.getUserId()).orElse(null));
    }

    /**
     * Retrieves a portfolio by its unique slug.
     *
     * @param slug the unique slug identifying the portfolio
     * @return a {@link PortfolioResponse} containing the portfolio data
     * @throws ResourceNotFoundException if the portfolio or associated user is not found
     */
    @Transactional(readOnly = true)
    @Override
    public PortfolioResponse getPortfolioBySlug(String slug) {
        Optional<Student> studentOpt = studentRepository.findByPortfolioSlug(slug);
        if (studentOpt.isEmpty()) {
            log.error("PortfolioServiceImpl: Portfolio not found for slug: {}", slug);
            throw new ResourceNotFoundException("Portfolio not found for slug: " + slug);
        }
        Student student = studentOpt.get();

        Optional<User> userOpt = userRepository.findById(student.getUserId());
        if (userOpt.isEmpty()) {
            log.error("PortfolioServiceImpl: User not found for ID: {}", student.getUserId());
            throw new ResourceNotFoundException("User not found");
        }
        User user = userOpt.get();

        PortfolioConfig config = portfolioConfigRepository.findByUser_UserId(student.getUserId());
        List<StudentSkill> skills = studentSkillRepository.findByStudent_UserId(student.getUserId());
        List<PortfolioProject> projects = portfolioProjectRepository.findByUser_UserId(user.getUserId());
        List<StudentEducation> education = studentEducationRepository.findByUser_UserId(student.getUserId());

        List<StudentSkillEvidence> evidence = liveEvidence(student.getUserId());

        return portfolioMapper.toPortfolioResponse(user, student, config, skills, projects, education, evidence,
                roadmapService.getStudentRoadmapForPortfolio(student),
                studentLevelService.levelOf(student.getUserId()).orElse(null));
    }

    /**
     * Upserts (updates or inserts) the portfolio data for the currently authenticated student.
     *
     * @param request the {@link PortfolioUpsertRequest} containing the new portfolio data
     * @return a {@link PortfolioResponse} with the updated portfolio data
     * @throws ResourceNotFoundException if the associated user is not found
     */
    @Transactional
    @Override
    public PortfolioResponse upsertPortfolio(PortfolioUpsertRequest request) {
        // Get or create the authenticated student for updates
        Student student = authenticatedStudentService.getOrCreateStudentForUpdate();
        // Fetch the corresponding user entity
        Optional<User> userOpt = userRepository.findById(student.getUserId());
        if (userOpt.isEmpty()) {
            log.error("PortfolioServiceImpl: User not found for ID: {}", student.getUserId());
            throw new ResourceNotFoundException("User not found");
        }
        User user = userOpt.get();

        // Synchronize the various sections of the portfolio based on the request
        syncPortfolioConfig(request.getConfig(), student);
        syncPortfolioProjects(request.getProjects(), user);
        syncPortfolioEducation(request.getEducation(), student);

        // Return the fully updated portfolio
        return getPortfolio();
    }

    @Transactional
    @Override
    public void requestReview(RequestReviewRequest request) {
        Student student = authenticatedStudentService.getRequiredStudent();
        User mentor = userRepository.findByEmail(request.getEmail());
        if (mentor == null || mentor.getRole() != UserRole.MENTOR) {
            throw new ResourceNotFoundException("Mentor not found");
        }

        boolean exists = portfolioReviewRequestRepository.existsByStudent_UserIdAndMentor_UserIdAndStatus(
                student.getUserId(),
                mentor.getUserId(),
                ReviewStatus.PENDING
        );
        if (exists) {
            throw new BadRequestException("You already have a pending review request with this mentor.");
        }

        User studentUser = userRepository.findById(student.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Student user not found"));

        portfolioReviewRequestRepository.save(PortfolioReviewRequest.builder()
                .student(studentUser)
                .mentor(mentor)
                .status(ReviewStatus.PENDING)
                .build());
    }

    /**
     * Synchronizes the portfolio configuration for a given student.
     *
     * @param configRequest the configuration request containing theme and section preferences
     * @param student the {@link Student} whose configuration is being updated
     */
    private void syncPortfolioConfig(PortfolioConfigRequest configRequest, Student student) {
        if (configRequest == null) return;
        PortfolioConfig config = portfolioConfigRepository.findByUser_UserId(student.getUserId());
        if (config == null) {
            config = PortfolioConfig.builder().user(student).build();
        }
        config.setTheme(configRequest.getTheme());
        config.setThemeColors(configRequest.getThemeColors());
        config.setFonts(configRequest.getFonts());
        config.setHeroSection(configRequest.getHeroSection());
        config.setSkillsSection(configRequest.getSkillsSection());
        portfolioConfigRepository.save(config);
    }

    /**
     * Synchronizes the portfolio projects for a given user.
     * Deletes existing projects and saves the new ones.
     *
     * @param projectRequests the list of project requests to save
     * @param user the {@link User} whose projects are being updated
     */
    private void syncPortfolioProjects(List<PortfolioProjectRequest> projectRequests, User user) {
        if (projectRequests == null) return;
        portfolioProjectRepository.deleteByUser_UserId(user.getUserId());
        List<PortfolioProject> newProjects = portfolioMapper.toPortfolioProjects(projectRequests, user);
        portfolioProjectRepository.saveAll(newProjects);
    }

    /**
     * Synchronizes the student's education history.
     * Deletes existing education records and saves the new ones.
     *
     * @param educationRequests the list of education requests to save
     * @param student the {@link Student} whose education records are being updated
     */
    private void syncPortfolioEducation(List<StudentEducationRequest> educationRequests, Student student) {
        if (educationRequests == null) return;
        studentEducationRepository.deleteByUser_UserId(student.getUserId());
        List<StudentEducation> newEducation = portfolioMapper.toStudentEducationList(educationRequests, student);
        studentEducationRepository.saveAll(newEducation);
    }


}
