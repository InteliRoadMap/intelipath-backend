package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.response.SkillResponse;
import com.inteliroadmap.backend.domain.dto.response.StudentResponse;
import com.inteliroadmap.backend.domain.entity.*;
import com.inteliroadmap.backend.domain.enums.RoadmapStepStatus;
import com.inteliroadmap.backend.services.AuthenticatedStudentService;
import com.inteliroadmap.backend.mappers.SkillMapper;
import com.inteliroadmap.backend.mappers.StudentDashboardMapper;
import com.inteliroadmap.backend.mappers.StudentMapper;
import com.inteliroadmap.backend.repositories.*;
import com.inteliroadmap.backend.services.StudentService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * Implementation of the {@link StudentService}.
 * Provides services for managing student profiles, their career settings, and skill progress tracking.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StudentServiceImpl implements StudentService {

    private static final int AI_TEXT_LIMIT = 80;

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
    private final AuthenticatedStudentService AuthenticatedStudentService;

    /**
     * Sets up or updates the student's profile information such as university, major, admission year, and target career.
     *
     * @param request the request containing the profile setup information
     * @return StudentResponse containing the updated profile
     * @throws com.inteliroadmap.backend.exceptions.ResourceNotFoundException if the user or career role is not found
     */
    @Transactional
    @Override
    public StudentResponse setupStudentProfile(com.inteliroadmap.backend.domain.dto.request.SetupStudentProfileRequest request) {
        log.info("Student Module: Setup Student Profile Request received");

        Student student = AuthenticatedStudentService.getOrCreateStudentForUpdate();
        User user = userRepository.findByUserId(student.getUserId());
        if (user == null) {
            throw new com.inteliroadmap.backend.exceptions.ResourceNotFoundException("User not found");
        }

        if (request.getUniversity() != null) student.setUniversity(request.getUniversity());
        if (request.getYearOfAdmission() != null) {
            student.setYearOfAdmission(request.getYearOfAdmission());
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
            student.setCareerId(career.getCareerId());
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

    /**
     * Retrieves the profile information of the currently authenticated student.
     *
     * @return StudentResponse containing the student's details
     */
    @Transactional
    @Override
    public StudentResponse getStudentProfile() {
        log.info("Student profile retrieval request received");
        Student student = AuthenticatedStudentService.getRequiredStudent();
        return studentMapper.toProfileResponse(student);
    }

    /**
     * Updates the target career for the currently authenticated student.
     *
     * @param careerId the UUID of the new target career
     * @return StudentResponse containing the updated profile
     * @throws com.inteliroadmap.backend.exceptions.ResourceNotFoundException if the career role is not found
     */
    @Transactional
    @Override
    public StudentResponse updateTargetCareer(UUID careerId) {
        log.info("Student target career update request received. careerId: {}", careerId);
        Student student = AuthenticatedStudentService.getOrCreateStudentForUpdate();
        CareerRole career = careerRoleRepository.findByCareerId(careerId);
        if (career == null) {
            throw new com.inteliroadmap.backend.exceptions.ResourceNotFoundException("Career role not found");
        }
        student.setCareerId(career.getCareerId());
        studentRepository.save(student);
        log.info("Student target career updated successfully. careerId: {}", careerId);
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
        log.info("Student Dashboard Module: Comparing selected skills with required skills");

        // Fetch current student and verify they exist
        Student student = getCurrentStudent();
        if (student == null) {
            return SkillResponse.builder().build();
        }

        // Fetch the list of skills the student has explicitly selected
        List<StudentSkill> selectedSkills = studentSkillRepository.findByStudentId(student.getUserId());
        if (student.getCareerId() == null) {
            // Return early if no career is selected, showing only what the student selected
            return SkillResponse.builder()
                    .selectedSkills(skillMapper.toSelectedSkillResponses(selectedSkills))
                    .build();
        }

        // Fetch the required skills for the target career
        List<CareerRequiredSkill> requiredSkills = careerRequiredSkillRepository
                .findByCareerRoleId(student.getCareerId());
                
        // Filter out required skills that the student already possesses to find the missing ones
        List<CareerRequiredSkill> missingRequiredSkills = filterMissingRequiredSkills(requiredSkills, selectedSkills);
        List<Skill> missingSkills = missingRequiredSkills.stream()
                .map(req -> skillRepository.findById(req.getSkillId()).orElse(null))
                .filter(Objects::nonNull)
                .toList();

        // Map required skills and calculate progress for each to populate the response
        List<com.inteliroadmap.backend.domain.dto.response.RequiredSkillResponse> requiredSkillResponses = skillMapper.toRequiredSkillResponses(requiredSkills);
        requiredSkillResponses.forEach(res -> {
            requiredSkills.stream()
                .filter(r -> r.getSkillId().equals(res.getSkill().getSkillId()))
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
        return AuthenticatedStudentService.getOrCreateStudent();
    }

    /**
     * Finds the required skills for a student's target career that they have not yet acquired.
     *
     * @param student the student whose skill gaps need checking
     * @return a list of missing {@link CareerRequiredSkill} entities
     */
    @Override
    public List<CareerRequiredSkill> findMissingRequiredSkills(Student student) {
        List<CareerRequiredSkill> requiredSkills = careerRequiredSkillRepository
                .findByCareerRoleId(student.getCareerId());
        List<StudentSkill> selectedSkills = studentSkillRepository.findByStudentId(student.getUserId());
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
                .map(StudentSkill::getSkillId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        return requiredSkills.stream()
                .filter(requiredSkill -> requiredSkill.getSkillId() != null)
                .filter(requiredSkill -> !selectedSkillIds.contains(requiredSkill.getSkillId()))
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
        if (studentSkillRepository.existsByStudentIdAndSkillId(student.getUserId(), skillId)) {
            return 100;
        }
        List<SkillNode> allNodesForSkill = skillNodeRepository.findBySkillIdAndCareerId(skillId, student.getCareerId());
        if (allNodesForSkill.isEmpty()) {
            return 0;
        }
        int completedCount = 0;
        for (SkillNode skillNode : allNodesForSkill) {
            StudentProgress nodeProgress = studentProgressRepository.findByStudentIdAndNodeId(student.getUserId(), skillNode.getNodeId()).orElse(null);
            if (nodeProgress != null && nodeProgress.getStatus() == RoadmapStepStatus.COMPLETED) {
                completedCount++;
            }
        }
        return (int) Math.round((double) completedCount / allNodesForSkill.size() * 100);
    }
}
