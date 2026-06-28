package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.response.SkillResponse;
import com.inteliroadmap.backend.domain.dto.response.StudentResponse;
import com.inteliroadmap.backend.domain.entity.*;
import com.inteliroadmap.backend.domain.enums.RoadmapStepStatus;
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
public class StudentServiceImpl {

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
    private final AuthenticatedStudentServiceImpl AuthenticatedStudentService;

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

        List<com.inteliroadmap.backend.domain.dto.response.RequiredSkillResponse> requiredSkillResponses = skillMapper.toRequiredSkillResponses(requiredSkills);
        requiredSkillResponses.forEach(res -> {
            requiredSkills.stream()
                .filter(r -> r.getSkill().getSkillId().equals(res.getSkill().getSkillId()))
                .findFirst()
                .ifPresent(r -> res.setProgress(calculateSkillProgress(student, r.getSkill())));
        });

        return SkillResponse.builder()
                .selectedSkills(skillMapper.toSelectedSkillResponses(selectedSkills))
                .requiredSkills(requiredSkillResponses)
                .missingSkills(skillMapper.toSkillItemResponses(missingSkills))
                .build();
    }

    private Student getCurrentStudent() {
        return AuthenticatedStudentService.getOrCreateStudent();
    }

    public List<CareerRequiredSkill> findMissingRequiredSkills(Student student) {
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

    public Integer calculateSkillProgress(Student student, Skill skill) {
        if (studentSkillRepository.existsByStudentAndSkill(student, skill)) {
            return 100;
        }
        List<SkillNode> allNodesForSkill = skillNodeRepository.findBySkillIdAndCareerRole_CareerId(skill.getSkillId(), student.getCareerRole().getCareerId());
        if (allNodesForSkill.isEmpty()) {
            return 0;
        }
        int completedCount = 0;
        for (SkillNode skillNode : allNodesForSkill) {
            StudentProgress nodeProgress = studentProgressRepository.findByStudentAndSkillNode(student, skillNode).orElse(null);
            if (nodeProgress != null && nodeProgress.getStatus() == RoadmapStepStatus.COMPLETED) {
                completedCount++;
            }
        }
        return (int) Math.round((double) completedCount / allNodesForSkill.size() * 100);
    }
}
