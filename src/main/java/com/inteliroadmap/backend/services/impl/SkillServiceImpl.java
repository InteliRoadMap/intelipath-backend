package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.request.CompareStRmSkillRequest;
import com.inteliroadmap.backend.domain.dto.request.ImportSkillsRequest;
import com.inteliroadmap.backend.domain.dto.response.roadmap.SkillResponse;
import com.inteliroadmap.backend.domain.entity.CareerRequiredSkill;
import com.inteliroadmap.backend.domain.entity.CareerRole;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.StudentSkill;
import com.inteliroadmap.backend.components.CoreSkillEligibility;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.services.AuthenticatedStudentService;
import com.inteliroadmap.backend.mappers.SkillMapper;
import com.inteliroadmap.backend.repositories.CareerRequiredSkillRepository;
import com.inteliroadmap.backend.repositories.CareerRoleRepository;
import com.inteliroadmap.backend.repositories.SkillRepository;
import com.inteliroadmap.backend.repositories.StudentSkillRepository;
import com.inteliroadmap.backend.services.SkillEvidenceService;
import com.inteliroadmap.backend.services.SkillService;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of the {@link SkillService}.
 * Provides services related to skill management, search, selection, and career comparison.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SkillServiceImpl implements SkillService {

    private final SkillRepository skillRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final CareerRoleRepository careerRoleRepository;
    private final CareerRequiredSkillRepository careerRequiredSkillRepository;
    private final AuthenticatedStudentService authenticatedStudentService;
    private final SkillEvidenceService skillEvidenceService;
    private final SkillMapper skillMapper;
    private final CoreSkillEligibility coreSkillEligibility;

    /**
     * Retrieves the authenticated student's selected skills and all skills available in the system.
     *
     * @return response containing selected skills and every available skill
     */
    @Transactional
    @Override
    public SkillResponse getStudentSkills() {
        log.info("SkillServiceImpl: Student skill retrieval request received");

        // Step 1: Get or create the authenticated student
        Student student = authenticatedStudentService.getOrCreateStudent();

        // Step 2: Load the student's selected skills and the ones they could declare
        List<StudentSkill> selectedSkills = studentSkillRepository.findByStudent_UserId(student.getUserId());
        List<Skill> allSkills = declarableSkills();

        // Step 3: Return selected and available skills when no target career is set
        if (student.getCareerRole() == null || student.getCareerRole().getCareerId() == null) {
            return SkillResponse.builder()
                    .selectedSkills(skillMapper.toSelectedSkillResponses(selectedSkills))
                    .skills(skillMapper.toSkillItemResponses(allSkills))
                    .build();
        }

        // Step 4: Load required skills for the selected career
        List<CareerRequiredSkill> requiredSkills = careerRequiredSkillRepository
                .findByCareerRole_CareerId(student.getCareerRole().getCareerId());

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
    @Override
    public SkillResponse searchSkills(String search) {
        log.info("SkillServiceImpl: Skill search request received. search: {}", search);

        // Step 1: Search only by skill name without case sensitivity, over the same
        // population the picker lists. Searching the raw catalog here would put back
        // exactly what step 2 of getStudentSkills takes out — a student typing "ma"
        // would be offered `$match` again.
        String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<Skill> skills = declarableSkills().stream()
                .filter(skill -> skill.getSkillName() != null
                        && skill.getSkillName().toLowerCase(Locale.ROOT).contains(needle))
                .toList();

        // Step 2: Return search results in the skills field
        return SkillResponse.builder()
                .skills(skillMapper.toSkillItemResponses(skills))
                .build();
    }

    /**
     * The skills a student is offered to declare.
     *
     * <p>Two gates, and both are needed. {@link SkillRepository#findDeclarableCandidates()}
     * asks whether anything outside the catalog vouches for the row — a posting, or a
     * career grade. {@link CoreSkillEligibility} asks whether the string is the name of one
     * learnable thing at all. Measured on the live database: 3.895 rows in the catalog,
     * 720 pass the evidence gate, and the name gate takes that to the list a person can
     * actually read.
     *
     * <p>Nothing is deleted and nothing is hidden from the roadmap — {@code $elemMatch} is
     * still a node inside the MongoDB track, and still something to learn. It is simply
     * not something a student says they can do.
     */
    private List<Skill> declarableSkills() {
        return skillRepository.findDeclarableCandidates().stream()
                .filter(skill -> coreSkillEligibility.isNameable(skill.getSkillName()))
                .toList();
    }

    /**
     * Imports a selected list of skills and associates them with the authenticated student.
     * Safely ignores any existing skills to prevent duplicates.
     *
     * @param request The payload containing the list of skills to import
     * @return SkillResponse containing the updated list of the student's skills
     */
    @Transactional
    @Override
    public SkillResponse importStudentSkills(ImportSkillsRequest request) {
        log.info("SkillServiceImpl: Student skill selection request received");

        // Step 1: Get the authenticated student and lock profile data for update
        Student student = authenticatedStudentService.getOrCreateStudentForUpdate();

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
            log.warn("SkillServiceImpl: Requested skills were not found: {}", missingSkillIds);
            throw new ResourceNotFoundException("Skills not found: " + missingSkillIds);
        }

        // Step 5: Load skill IDs that are already assigned to the student
        List<StudentSkill> existingStudentSkills = studentSkillRepository
                .findByStudent_UserIdAndSkill_SkillIdIn(student.getUserId(), List.copyOf(requestedSkillIds));
        Set<UUID> existingSkillIds = new LinkedHashSet<>();
        for (StudentSkill existingStudentSkill : existingStudentSkills) {
            existingSkillIds.add(existingStudentSkill.getSkill().getSkillId());
        }

        // Step 6: Build only student-skill records that do not already exist
        List<StudentSkill> newSkillsToSave = new ArrayList<>();
        for (Skill requestedSkill : requestedSkills) {
            if (!existingSkillIds.contains(requestedSkill.getSkillId())) {
                StudentSkill newStudentSkill = StudentSkill.builder()
                        .student(Student.builder().userId(student.getUserId()).build())
                        .skill(requestedSkill)
                        .build();
                newSkillsToSave.add(newStudentSkill);
            }
        }

        // Step 7: Save new student-skill records in one operation
        if (!newSkillsToSave.isEmpty()) {
            studentSkillRepository.saveAll(newSkillsToSave);

            // Step 7b: Record where these skills came from. A self-declaration has to be
            // traceable as such, otherwise it is indistinguishable downstream from a skill
            // proven by a GitHub repository or a passed FPT subject.
            List<Skill> declaredSkills = new ArrayList<>();
            for (StudentSkill savedSkill : newSkillsToSave) {
                declaredSkills.add(savedSkill.getSkill());
            }
            skillEvidenceService.recordSelfDeclaredEvidence(student.getUserId(), declaredSkills);
        }

        // Step 8: Return the complete selected skill list after the update
        List<StudentSkill> studentSkills = studentSkillRepository.findByStudent_UserId(student.getUserId());
        log.info("SkillServiceImpl: Student skill selection completed. selectedSkillCount: {}", studentSkills.size());

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
    @Override
    public SkillResponse compareWithStudentSkills(CompareStRmSkillRequest request) {
        log.info("SkillServiceImpl: Student skill comparison request received. careerId: {}", request.getCareerId());

        // Step 1: Get or create the authenticated student
        Student student = authenticatedStudentService.getOrCreateStudent();

        // Step 2: Verify that the requested career exists
        Optional<CareerRole> careerOptional = careerRoleRepository.findById(request.getCareerId());
        if (careerOptional.isEmpty()) {
            log.warn("SkillServiceImpl: Career role was not found: {}", request.getCareerId());
            throw new ResourceNotFoundException("Career not found");
        }

        CareerRole career = careerOptional.get();

        // Step 3: Load career requirements and the student's selected skills
        List<CareerRequiredSkill> requiredSkills = careerRequiredSkillRepository.findByCareerRole_CareerId(career.getCareerId());
        List<StudentSkill> studentSkills = studentSkillRepository.findByStudent_UserId(student.getUserId());

        // Step 4: Calculate missing skills
        List<Skill> missingSkills = findMissingSkills(requiredSkills, studentSkills);

        // Step 5: Map comparison results to the API response
        return SkillResponse.builder()
                .selectedSkills(skillMapper.toSelectedSkillResponses(studentSkills))
                .requiredSkills(skillMapper.toRequiredSkillResponses(requiredSkills))
                .missingSkills(skillMapper.toSkillItemResponses(missingSkills))
                .build();
    }

    /**
     * Finds the skills that are required for a career but are missing from the student's selected skills.
     *
     * @param requiredSkills the list of skills required by the career
     * @param studentSkills the list of skills currently selected by the student
     * @return a list of missing {@link Skill} entities
     */
    private List<Skill> findMissingSkills(
            List<CareerRequiredSkill> requiredSkills,
            List<StudentSkill> studentSkills
    ) {
        // Collect all skill IDs currently possessed by the student
        Set<UUID> studentSkillIds = new LinkedHashSet<>();
        for (StudentSkill studentSkill : studentSkills) {
            studentSkillIds.add(studentSkill.getSkill().getSkillId());
        }

        List<Skill> missingSkills = new ArrayList<>();
        // Check each required skill to see if the student already has it
        for (CareerRequiredSkill requiredSkill : requiredSkills) {
            Skill skill = skillRepository.findById(requiredSkill.getSkill().getSkillId()).orElse(null);
            // If the required skill exists and is not in the student's list, it is missing
            if (skill != null && !studentSkillIds.contains(skill.getSkillId())) {
                missingSkills.add(skill);
            }
        }

        return missingSkills;
    }
}
