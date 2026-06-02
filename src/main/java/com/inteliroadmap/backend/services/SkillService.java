package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.CompareStRmSkillRequest;
import com.inteliroadmap.backend.domain.dto.request.ImportSkillsRequest;
import com.inteliroadmap.backend.domain.dto.response.SkillResponse;
import com.inteliroadmap.backend.domain.entity.*;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.repositories.*;
import jakarta.transaction.Transactional;
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

    private final SkillRepository skillRepository;
    private final StudentRepository studentRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final CareerRoleRepository careerRoleRepository;
    private final CareerRequiredSkillRepository careerRequiredSkillRepository;

    @Transactional
    public SkillResponse getStudentSkills(UUID student_id) {
        log.info("Skill Module: Get Student Skill Request received");

        Student student = studentRepository.findByStudentId(student_id);
        if (student == null) {
            throw new ResourceNotFoundException("Student not found");
        }

        List<StudentSkill> skills = studentSkillRepository.findByStudent(student);

        return SkillResponse.builder()
                .studentSkills(skills)
                .build();
    }

    @Transactional
    public SkillResponse getSkillsByCategory(String category) {
        log.info("Skill Module: Get Skill By Category Request received");

        List<Skill> skills = skillRepository.findByCategory(category);

        return SkillResponse.builder()
                .skills(skills)
                .build();
    }

    @Transactional
    public SkillResponse importStudentSkills(ImportSkillsRequest request) {
        log.info("Skill Module: Import Student Skills Request received");

        Student student = studentRepository.findByStudentId(request.getStudentId());
        if (student == null) {
            throw new ResourceNotFoundException("Student not found");
        }

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

    @Transactional
    public SkillResponse compareWithStudentSkills(CompareStRmSkillRequest request) {
        log.info("Skill Module: Comparing Student Skills with Roadmap Required Skills");

        Student student = studentRepository.findByStudentId(request.getStudentId());
        if (student == null) {
            throw new ResourceNotFoundException("Student not found");
        }

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
