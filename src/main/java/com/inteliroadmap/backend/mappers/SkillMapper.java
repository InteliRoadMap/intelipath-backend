package com.inteliroadmap.backend.mappers;

import com.inteliroadmap.backend.domain.dto.response.RequiredSkillResponse;
import com.inteliroadmap.backend.domain.dto.response.SkillItemResponse;
import com.inteliroadmap.backend.domain.entity.CareerRequiredSkill;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.entity.StudentSkill;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SkillMapper {

    public SkillItemResponse toSkillItemResponse(Skill skill) {
        return SkillItemResponse.builder()
                .skillId(skill.getSkillId())
                .skillName(skill.getSkillName())
                .category(skill.getCategory())
                .career(skill.getCareer())
                .build();
    }

    public List<SkillItemResponse> toSkillItemResponses(List<Skill> skills) {
        return skills.stream()
                .map(this::toSkillItemResponse)
                .toList();
    }

    public List<SkillItemResponse> toSelectedSkillResponses(List<StudentSkill> studentSkills) {
        return studentSkills.stream()
                .map(StudentSkill::getSkill)
                .map(this::toSkillItemResponse)
                .toList();
    }

    public List<RequiredSkillResponse> toRequiredSkillResponses(List<CareerRequiredSkill> requiredSkills) {
        return requiredSkills.stream()
                .map(requiredSkill -> RequiredSkillResponse.builder()
                        .skill(toSkillItemResponse(requiredSkill.getSkill()))
                        .importanceLevel(requiredSkill.getImportanceLevel())
                        .build())
                .toList();
    }
}
