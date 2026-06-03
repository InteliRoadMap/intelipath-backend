package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.CompareStRmSkillRequest;
import com.inteliroadmap.backend.domain.dto.request.ImportSkillsRequest;
import com.inteliroadmap.backend.domain.dto.response.SkillResponse;
import com.inteliroadmap.backend.domain.entity.*;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.repositories.*;
import jakarta.transaction.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SkillService {

    private final UserRepository userRepository;
    private final SkillRepository skillRepository;
    private final StudentRepository studentRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final CareerRoleRepository careerRoleRepository;
    private final CareerRequiredSkillRepository careerRequiredSkillRepository;

    /**
     * Securely identifies the currently authenticated student.
     * Extracts the user email from the SecurityContextHolder and retrieves the corresponding Student profile.
     *
     * @return The authenticated Student entity
     * @throws ResourceNotFoundException if the token is invalid or the student profile is missing
     */
    private Student getAuthenticatedStudent() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new ResourceNotFoundException("User not found from token");
        }
        Student student = studentRepository.findByStudentId(user.getUserId());
        if (student == null) {
            throw new ResourceNotFoundException("Student profile not found");
        }
        return student;
    }

    /**
     * Retrieves all skills currently associated with the authenticated student.
     *
     * @return SkillResponse containing the list of the student's skills
     */
    @Transactional
    public SkillResponse getStudentSkills() {
        log.info("Skill Module: Get Student Skill Request received");

        Student student = getAuthenticatedStudent();

        List<StudentSkill> skills = studentSkillRepository.findByStudent(student);

        return SkillResponse.builder()
                .studentSkills(skills)
                .build();
    }

    /**
     * Fetches all available skills in the system that match the search query in their category or career.
     *
     * @param search The search string to filter by (e.g., "Frontend", "Backend", "Core")
     * @return SkillResponse containing the list of matching skills
     */
    @Transactional
    public SkillResponse searchSkills(String search) {
        log.info("Skill Module: Search Skills Request received for query: {}", search);

        List<Skill> skills =
                skillRepository
                        .findByCategoryContainingIgnoreCaseOrCareerContainingIgnoreCase(search, search);

        return SkillResponse.builder()
                .skills(skills)
                .build();
    }

    /**
     * Imports a selected list of skills and associates them with the authenticated student.
     * Safely ignores any existing skills to prevent duplicates.
     *
     * @param request The payload containing the list of skills to import
     * @return SkillResponse containing the updated list of the student's skills
     */
    @Transactional
    public SkillResponse importStudentSkills(ImportSkillsRequest request) {
        log.info("Skill Module: Import Student Skills Request received");

        Student student = getAuthenticatedStudent();

        List<StudentSkill> studentSkills = studentSkillRepository.findByStudent(student);
        List<UUID> existingSkillIds = studentSkills.stream()
                .map(ss -> ss.getSkill().getSkillId())
                .toList();

        List<Skill> skills = request.getSkillList();
        List<StudentSkill> newSkillsToSave = new java.util.ArrayList<>();

        for (Skill s : skills) {
            if (!existingSkillIds.contains(s.getSkillId())) {
                StudentSkill newStudentSkill = StudentSkill.builder()
                        .student(student)
                        .skill(s)
                        .build();
                newSkillsToSave.add(newStudentSkill);
            }
        }

        if (!newSkillsToSave.isEmpty()) {
            studentSkillRepository.saveAll(newSkillsToSave);
            studentSkills.addAll(newSkillsToSave);
        }

        return SkillResponse.builder()
                .studentSkills(studentSkills)
                .build();
    }

    /**
     * Compares the authenticated student's current skills against the skills required by a specific career.
     * Calculates and returns the list of missing skills that the student needs to acquire.
     *
     * @param request The payload containing the target career ID
     * @return SkillResponse detailing current skills, required skills, and missing skills
     */
    @Transactional
    public SkillResponse compareWithStudentSkills(CompareStRmSkillRequest request) {
        log.info("Skill Module: Comparing Student Skills with Roadmap Required Skills");

        Student student = getAuthenticatedStudent();

        CareerRole career = careerRoleRepository.findById(request.getCareerId())
                .orElseThrow(() -> new ResourceNotFoundException("Career not found"));

        List<CareerRequiredSkill> requiredSkills = careerRequiredSkillRepository.findByCareerRole(career);
        List<StudentSkill> studentSkills = studentSkillRepository.findByStudent(student);

        List<UUID> studentSkillIds = studentSkills.stream()
                .map(ss -> ss.getSkill().getSkillId())
                .toList();

        List<Skill> missingSkills = requiredSkills.stream()
                .map(CareerRequiredSkill::getSkill)
                .filter(skill -> !studentSkillIds.contains(skill.getSkillId()))
                .toList();

        return SkillResponse.builder()
                .studentSkills(studentSkills)
                .careerRequiredSkills(requiredSkills)
                .skills(missingSkills)
                .build();
    }
}
