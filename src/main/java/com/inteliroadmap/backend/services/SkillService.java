package com.inteliroadmap.backend.services;

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

import java.util.LinkedHashSet;
import java.util.List;
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
        log.info("Fetching selected skills and all available skills for the authenticated student");

        Student student = authenticatedStudentHelper.getOrCreateStudent();

        List<StudentSkill> selectedSkills = studentSkillRepository.findByStudent(student);
        List<Skill> allSkills = skillRepository.findAll();

        return SkillResponse.builder()
                .selectedSkills(skillMapper.toSelectedSkillResponses(selectedSkills))
                .skills(skillMapper.toSkillItemResponses(allSkills))
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
        log.info("Searching skills by name: {}", search);

        List<Skill> skills = skillRepository.findBySkillNameContainingIgnoreCase(search);

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
        log.info("Skill Module: Import Student Skills Request received");

        Student student = authenticatedStudentHelper.getOrCreateStudentForUpdate();
        Set<UUID> requestedSkillIds = new LinkedHashSet<>(request.getSkillIds());
        List<Skill> requestedSkills = skillRepository.findAllById(requestedSkillIds);

        Set<UUID> foundSkillIds = requestedSkills.stream()
                .map(Skill::getSkillId)
                .collect(java.util.stream.Collectors.toSet());
        Set<UUID> missingSkillIds = new LinkedHashSet<>(requestedSkillIds);
        missingSkillIds.removeAll(foundSkillIds);
        if (!missingSkillIds.isEmpty()) {
            throw new ResourceNotFoundException("Skills not found: " + missingSkillIds);
        }

        Set<UUID> existingSkillIds = studentSkillRepository
                .findByStudentAndSkill_SkillIdIn(student, List.copyOf(requestedSkillIds))
                .stream()
                .map(studentSkill -> studentSkill.getSkill().getSkillId())
                .collect(java.util.stream.Collectors.toSet());

        List<StudentSkill> newSkillsToSave = requestedSkills.stream()
                .filter(skill -> !existingSkillIds.contains(skill.getSkillId()))
                .map(skill -> StudentSkill.builder()
                        .student(student)
                        .skill(skill)
                        .build())
                .toList();

        if (!newSkillsToSave.isEmpty()) {
            studentSkillRepository.saveAll(newSkillsToSave);
        }

        List<StudentSkill> studentSkills = studentSkillRepository.findByStudent(student);
        return SkillResponse.builder()
                .selectedSkills(skillMapper.toSelectedSkillResponses(studentSkills))
                .build();
    }


}
