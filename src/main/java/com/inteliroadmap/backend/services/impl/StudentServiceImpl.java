package com.inteliroadmap.backend.services.impl;

import java.time.LocalDate;

import com.inteliroadmap.backend.components.RoadmapProgressCalculator;
import com.inteliroadmap.backend.domain.dto.request.SetupStudentProfileRequest;
import com.inteliroadmap.backend.domain.dto.response.roadmap.RequiredSkillResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.SkillResponse;
import com.inteliroadmap.backend.domain.dto.response.student.StudentResponse;
import com.inteliroadmap.backend.domain.entity.CareerRequiredSkill;
import com.inteliroadmap.backend.domain.entity.CareerRole;
import com.inteliroadmap.backend.domain.entity.RagDocument;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.entity.SkillNode;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.StudentProgress;
import com.inteliroadmap.backend.domain.entity.StudentSkill;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.services.AuthenticatedStudentService;
import com.inteliroadmap.backend.services.DocumentIngestionService;
import com.inteliroadmap.backend.services.RagDocumentService;
import com.inteliroadmap.backend.services.SupabaseStorageService;
import com.inteliroadmap.backend.mappers.SkillMapper;
import com.inteliroadmap.backend.mappers.StudentDashboardMapper;
import com.inteliroadmap.backend.mappers.StudentMapper;
import com.inteliroadmap.backend.repositories.CareerRequiredSkillRepository;
import com.inteliroadmap.backend.repositories.CareerRoleRepository;
import com.inteliroadmap.backend.repositories.ChatMessageRepository;
import com.inteliroadmap.backend.repositories.ChatSessionRepository;
import com.inteliroadmap.backend.repositories.FeedbackRepository;
import com.inteliroadmap.backend.repositories.SkillNodeRepository;
import com.inteliroadmap.backend.repositories.SkillRepository;
import com.inteliroadmap.backend.repositories.SkillTrendRepository;
import com.inteliroadmap.backend.repositories.StudentProgressRepository;
import com.inteliroadmap.backend.repositories.StudentRepository;
import com.inteliroadmap.backend.repositories.StudentSkillRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.services.StudentService;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of the {@link StudentService}.
 * Provides services for managing student profiles, their career settings, and skill progress tracking.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StudentServiceImpl implements StudentService {

    private static final int AI_TEXT_LIMIT = 80;

    private final RoadmapProgressCalculator roadmapProgressCalculator;
    private final UserRepository userRepository;
    private final SkillRepository skillRepository;
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
    private final SupabaseStorageService supabaseStorageService;
    private final RagDocumentService ragDocumentService;
    private final DocumentIngestionService documentIngestionService;
    private final AuthenticatedStudentService authenticatedStudentService;

    /**
     * Sets up or updates the student's profile information such as university, major, admission year, and target career.
     *
     * @param request the request containing the profile setup information
     * @return StudentResponse containing the updated profile
     * @throws ResourceNotFoundException if the user or career role is not found
     */
    @Transactional
    @Override
    public StudentResponse setupStudentProfile(SetupStudentProfileRequest request) {
        log.info("StudentServiceImpl: Setup Student Profile Request received");

        Student student = authenticatedStudentService.getOrCreateStudentForUpdate();
        User user = userRepository.findByUserId(student.getUserId());
        if (user == null) {
            throw new ResourceNotFoundException("User not found");
        }

        if (request.getUniversityName() != null) {
            String universityName = request.getUniversityName().trim();
            student.setUniversityName(universityName.isEmpty() ? null : universityName);
        }
        if (request.getAdmissionDate() != null) {
            student.setAdmissionDate(request.getAdmissionDate());
        }

        if (request.getMajor() != null) {
            student.setMajor(request.getMajor());
        }

        if (request.getGithubProfile() != null) {
            student.setGithubProfile(request.getGithubProfile().trim());
        }

        if (request.getCareerId() != null) {
            CareerRole career = careerRoleRepository.findByCareerId(request.getCareerId());
            if (career == null) {
                log.warn("StudentServiceImpl: Career role was not found: {}", request.getCareerId());
                throw new ResourceNotFoundException("Career role not found");
            }
            student.setCareerRole(career);
        }

        boolean userChanged = false;
        if (request.getBio() != null) {
            user.setBio(request.getBio());
            userChanged = true;
        }
        if (request.getYob() != null && !request.getYob().trim().isEmpty()) {
            user.setYob(LocalDate.parse(request.getYob().trim()));
            userChanged = true;
        }

        if (userChanged) {
            userRepository.save(user);
        }

        studentRepository.save(student);

        log.info("StudentServiceImpl: Student profile updated successfully for user: {}", user.getEmail());
        return studentMapper.toProfileResponse(student);
    }

    /**
     * Retrieves the profile information of the currently authenticated student.
     *
     * @return StudentResponse containing the student's details
     */
    @Transactional
    @Override
    public StudentResponse getStudentProfile() {
        log.info("StudentServiceImpl: Student profile retrieval request received");
        Student student = authenticatedStudentService.getRequiredStudent();
        return studentMapper.toProfileResponse(student);
    }

    /**
     * Updates the target career for the currently authenticated student.
     *
     * @param careerId the UUID of the new target career
     * @return StudentResponse containing the updated profile
     * @throws ResourceNotFoundException if the career role is not found
     */
    @Transactional
    @Override
    public StudentResponse updateTargetCareer(UUID careerId) {
        log.info("StudentServiceImpl: Student target career update request received. careerId: {}", careerId);
        Student student = authenticatedStudentService.getOrCreateStudentForUpdate();
        CareerRole career = careerRoleRepository.findByCareerId(careerId);
        if (career == null) {
            throw new ResourceNotFoundException("Career role not found");
        }
        student.setCareerRole(career);
        studentRepository.save(student);
        log.info("StudentServiceImpl: Student target career updated successfully. careerId: {}", careerId);
        return studentMapper.toProfileResponse(student);
    }

    /**
     * Compare the current student's selected skills with required career skills.
     *
     * @return SkillResponse containing selected, required, and missing skills
     */
    @Transactional
    @Override
    public SkillResponse compareCurrentStudentSkills() {
        log.info("StudentServiceImpl: Comparing selected skills with required skills");

        // Fetch current student and verify they exist
        Student student = getCurrentStudent();
        if (student == null) {
            return SkillResponse.builder().build();
        }

        // Fetch the list of skills the student has explicitly selected
        List<StudentSkill> selectedSkills = studentSkillRepository.findByStudent_UserId(student.getUserId());
        if (student.getCareerRole() == null || student.getCareerRole().getCareerId() == null) {
            // Return early if no career is selected, showing only what the student selected
            return SkillResponse.builder()
                    .selectedSkills(skillMapper.toSelectedSkillResponses(selectedSkills))
                    .build();
        }

        // Fetch the required skills for the target career
        List<CareerRequiredSkill> requiredSkills = careerRequiredSkillRepository
                .findByCareerRole_CareerId(student.getCareerRole().getCareerId());
                
        // Filter out required skills that the student already possesses to find the missing ones
        List<CareerRequiredSkill> missingRequiredSkills = filterMissingRequiredSkills(requiredSkills, selectedSkills);
        List<Skill> missingSkills = missingRequiredSkills.stream()
                .map(req -> skillRepository.findById(req.getSkill().getSkillId()).orElse(null))
                .filter(Objects::nonNull)
                .toList();

        // Map required skills and calculate progress for each to populate the response
        List<RequiredSkillResponse> requiredSkillResponses = skillMapper.toRequiredSkillResponses(requiredSkills);
        requiredSkillResponses.forEach(res -> {
            requiredSkills.stream()
                .filter(r -> r.getSkill().getSkillId().equals(res.getSkill().getSkillId()))
                .findFirst()
                .ifPresent(r -> res.setProgress(calculateSkillProgress(student, res.getSkill().getSkillId())));
        });

        // Assemble the final SkillResponse comparing current skills with required skills
        return SkillResponse.builder()
                .selectedSkills(skillMapper.toSelectedSkillResponses(selectedSkills))
                .requiredSkills(requiredSkillResponses)
                .missingSkills(skillMapper.toSkillItemResponses(missingSkills))
                .build();
    }

    /**
     * Retrieves the currently authenticated student.
     *
     * @return the current {@link Student}
     */
    private Student getCurrentStudent() {
        return authenticatedStudentService.getOrCreateStudent();
    }

    /**
     * Finds the required skills for a student's target career that they have not yet acquired.
     *
     * @param student the student whose skill gaps need checking
     * @return a list of missing {@link CareerRequiredSkill} entities
     */
    @Override
    public List<CareerRequiredSkill> findMissingRequiredSkills(Student student) {
        if (student.getCareerRole() == null || student.getCareerRole().getCareerId() == null) {
            return List.of();
        }
        List<CareerRequiredSkill> requiredSkills = careerRequiredSkillRepository
                .findByCareerRole_CareerId(student.getCareerRole().getCareerId());
        List<StudentSkill> selectedSkills = studentSkillRepository.findByStudent_UserId(student.getUserId());
        return filterMissingRequiredSkills(requiredSkills, selectedSkills);
    }

    /**
     * Filters a list of required skills against the skills already selected by the student.
     *
     * @param requiredSkills list of required skills for a career
     * @param selectedSkills list of skills currently selected by the student
     * @return the filtered list of required skills that are still missing
     */
    private List<CareerRequiredSkill> filterMissingRequiredSkills(
            List<CareerRequiredSkill> requiredSkills,
            List<StudentSkill> selectedSkills
    ) {
        Set<UUID> selectedSkillIds = selectedSkills.stream()
                .map(s -> s.getSkill().getSkillId())
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        return requiredSkills.stream()
                .filter(requiredSkill -> requiredSkill.getSkill().getSkillId() != null)
                .filter(requiredSkill -> !selectedSkillIds.contains(requiredSkill.getSkill().getSkillId()))
                .toList();
    }

    /**
     * Calculates the progress percentage for a specific skill for a student.
     * Returns 100 if the student has explicitly selected the skill.
     * Otherwise, calculates based on completed skill nodes in the roadmap.
     *
     * @param student the student profile
     * @param skillId the UUID of the skill to calculate progress for
     * @return progress as an integer percentage (0-100)
     */
    @Override
    public Integer calculateSkillProgress(Student student, UUID skillId) {
        if (studentSkillRepository.existsByStudent_UserIdAndSkill_SkillId(student.getUserId(), skillId)) {
            return 100;
        }
        if (student.getCareerRole() == null || student.getCareerRole().getCareerId() == null) {
            return 0;
        }
        List<SkillNode> allNodesForSkill = skillNodeRepository.findBySkill_SkillIdAndCareerRole_CareerId(skillId, student.getCareerRole().getCareerId());
        if (allNodesForSkill.isEmpty()) {
            return 0;
        }
        List<UUID> nodeIds = allNodesForSkill.stream().map(SkillNode::getNodeId).toList();
        List<StudentProgress> progresses = studentProgressRepository
                .findByStudent_UserIdAndSkillNode_NodeIdIn(student.getUserId(), nodeIds);
        return roadmapProgressCalculator.calculateProgress(allNodesForSkill, progresses);
    }

    @Transactional
    @Override
    public StudentResponse uploadTranscript(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Transcript file is required");
        }
        Student student = authenticatedStudentService.getOrCreateStudentForUpdate();
        User user = userRepository.findByUserId(student.getUserId());
        if (user == null) {
            throw new ResourceNotFoundException("User not found");
        }

        String transcriptUrl = supabaseStorageService.uploadTranscript(file, student.getUserId().toString());
        RagDocument document = ragDocumentService.startStudentTranscript(user, transcriptUrl, file);

        try {
            documentIngestionService.ingestPdfDocument(file, document);
            ragDocumentService.markCompleted(document.getDocumentId());
        } catch (Exception exception) {
            ragDocumentService.markFailed(document.getDocumentId(), exception);
            throw new RuntimeException("Failed to process transcript for RAG", exception);
        }

        student.setTranscriptUrl(transcriptUrl);
        studentRepository.save(student);
        return studentMapper.toProfileResponse(student);
    }
}
