package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.CompareStRmSkillRequest;
import com.inteliroadmap.backend.domain.dto.request.ImportSkillsRequest;
import com.inteliroadmap.backend.domain.dto.response.SkillResponse;
import com.inteliroadmap.backend.domain.entity.*;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.helper.AuthenticatedStudentHelper;
import com.inteliroadmap.backend.mappers.SkillMapper;
import com.inteliroadmap.backend.repositories.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SkillService {

    private final SkillRepository skillRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final CareerRoleRepository careerRoleRepository;
    private final CareerRequiredSkillRepository careerRequiredSkillRepository;
    private final AuthenticatedStudentHelper authenticatedStudentHelper;
    private final SkillMapper skillMapper;

    /**
     * Retrieves the authenticated student's selected skills and all skills available in the system.
     *
     * @return response containing selected skills and every available skill
     */
    @Transactional
    public SkillResponse getStudentSkills() {
        log.info("Student skill retrieval request received");

        // Step 1: Get or create the authenticated student
        Student student = authenticatedStudentHelper.getOrCreateStudent();

        // Step 2: Load the student's selected skills and all available skills
        List<StudentSkill> selectedSkills = studentSkillRepository.findByStudent(student);
        List<Skill> allSkills = skillRepository.findAll();

        // Step 3: Return selected and available skills when no target career is set
        if (student.getCareerRole() == null) {
            return SkillResponse.builder()
                    .selectedSkills(skillMapper.toSelectedSkillResponses(selectedSkills))
                    .skills(skillMapper.toSkillItemResponses(allSkills))
                    .build();
        }

        // Step 4: Load required skills for the selected career
        List<CareerRequiredSkill> requiredSkills = careerRequiredSkillRepository
                .findByCareerRole(student.getCareerRole());

        // Step 5: Calculate skills that the student has not selected
        List<Skill> missingSkills = findMissingSkills(requiredSkills, selectedSkills);

        // Step 6: Map all skill groups to the API response
        return SkillResponse.builder()
                .selectedSkills(skillMapper.toSelectedSkillResponses(selectedSkills))
                .skills(skillMapper.toSkillItemResponses(allSkills))
                .requiredSkills(skillMapper.toRequiredSkillResponses(requiredSkills))
                .missingSkills(skillMapper.toSkillItemResponses(missingSkills))
                .build();
    }

    /**
     * Searches available skills by skill name without case sensitivity.
     *
     * @param search skill name fragment to search for
     * @return response containing matching skills
     */
    @Transactional
    public SkillResponse searchSkills(String search) {
        log.info("Skill search request received. search: {}", search);

        // Step 1: Search only by skill name without case sensitivity
        List<Skill> skills = skillRepository.findBySkillNameContainingIgnoreCase(search);

        // Step 2: Return search results in the skills field
        return SkillResponse.builder()
                .skills(skillMapper.toSkillItemResponses(skills))
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
        log.info("Student skill selection request received");

        // Step 1: Get the authenticated student and lock profile data for update
        Student student = authenticatedStudentHelper.getOrCreateStudentForUpdate();

        // Step 2: Remove duplicate skill IDs while preserving request order
        Set<UUID> requestedSkillIds = new LinkedHashSet<>(request.getSkillIds());

        // Step 3: Load all requested skills in one database query
        List<Skill> requestedSkills = skillRepository.findAllById(requestedSkillIds);

        // Step 4: Verify that every requested skill exists before saving anything
        Set<UUID> foundSkillIds = new LinkedHashSet<>();
        for (Skill requestedSkill : requestedSkills) {
            foundSkillIds.add(requestedSkill.getSkillId());
        }

        Set<UUID> missingSkillIds = new LinkedHashSet<>(requestedSkillIds);
        missingSkillIds.removeAll(foundSkillIds);
        if (!missingSkillIds.isEmpty()) {
            log.warn("Requested skills were not found: {}", missingSkillIds);
            throw new ResourceNotFoundException("Skills not found: " + missingSkillIds);
        }

        // Step 5: Load skill IDs that are already assigned to the student
        List<StudentSkill> existingStudentSkills = studentSkillRepository
                .findByStudentAndSkill_SkillIdIn(student, List.copyOf(requestedSkillIds));
        Set<UUID> existingSkillIds = new LinkedHashSet<>();
        for (StudentSkill existingStudentSkill : existingStudentSkills) {
            existingSkillIds.add(existingStudentSkill.getSkill().getSkillId());
        }

        // Step 6: Build only student-skill records that do not already exist
        List<StudentSkill> newSkillsToSave = new ArrayList<>();
        for (Skill requestedSkill : requestedSkills) {
            if (!existingSkillIds.contains(requestedSkill.getSkillId())) {
                StudentSkill newStudentSkill = StudentSkill.builder()
                        .student(student)
                        .skill(requestedSkill)
                        .build();
                newSkillsToSave.add(newStudentSkill);
            }
        }

        // Step 7: Save new student-skill records in one operation
        if (!newSkillsToSave.isEmpty()) {
            studentSkillRepository.saveAll(newSkillsToSave);
        }

        // Step 8: Return the complete selected skill list after the update
        List<StudentSkill> studentSkills = studentSkillRepository.findByStudent(student);
        log.info("Student skill selection completed. selectedSkillCount: {}", studentSkills.size());

        return SkillResponse.builder()
                .selectedSkills(skillMapper.toSelectedSkillResponses(studentSkills))
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
        log.info("Student skill comparison request received. careerId: {}", request.getCareerId());

        // Step 1: Get or create the authenticated student
        Student student = authenticatedStudentHelper.getOrCreateStudent();

        // Step 2: Verify that the requested career exists
        Optional<CareerRole> careerOptional = careerRoleRepository.findById(request.getCareerId());
        if (careerOptional.isEmpty()) {
            log.warn("Career role was not found: {}", request.getCareerId());
            throw new ResourceNotFoundException("Career not found");
        }

        CareerRole career = careerOptional.get();

        // Step 3: Load career requirements and the student's selected skills
        List<CareerRequiredSkill> requiredSkills = careerRequiredSkillRepository.findByCareerRole(career);
        List<StudentSkill> studentSkills = studentSkillRepository.findByStudent(student);

        // Step 4: Calculate missing skills
        List<Skill> missingSkills = findMissingSkills(requiredSkills, studentSkills);

        // Step 5: Map comparison results to the API response
        return SkillResponse.builder()
                .selectedSkills(skillMapper.toSelectedSkillResponses(studentSkills))
                .requiredSkills(skillMapper.toRequiredSkillResponses(requiredSkills))
                .missingSkills(skillMapper.toSkillItemResponses(missingSkills))
                .build();
    }

    private List<Skill> findMissingSkills(
            List<CareerRequiredSkill> requiredSkills,
            List<StudentSkill> studentSkills
    ) {
        Set<UUID> studentSkillIds = new LinkedHashSet<>();
        for (StudentSkill studentSkill : studentSkills) {
            studentSkillIds.add(studentSkill.getSkill().getSkillId());
        }

        List<Skill> missingSkills = new ArrayList<>();
        for (CareerRequiredSkill requiredSkill : requiredSkills) {
            Skill skill = requiredSkill.getSkill();
            if (!studentSkillIds.contains(skill.getSkillId())) {
                missingSkills.add(skill);
            }
        }

        return missingSkills;
    }
}
