package com.inteliroadmap.backend.mappers;

import com.inteliroadmap.backend.domain.dto.response.roadmap.RequiredSkillResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.SkillItemResponse;
import com.inteliroadmap.backend.domain.entity.CareerRequiredSkill;
import com.inteliroadmap.backend.domain.entity.CareerRole;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.entity.StudentSkill;
import com.inteliroadmap.backend.repositories.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SkillMapper {

    private final SkillRepository skillRepository;

    public SkillItemResponse toSkillItemResponse(Skill skill) {
        return SkillItemResponse.builder()
                .skillId(skill.getSkillId())
                .skillName(skill.getSkillName())
                .category(skill.getCategory())
                .career(skill.getCareers() != null && !skill.getCareers().isEmpty() ? skill.getCareers().stream().map(CareerRole::getCareerName).collect(Collectors.joining(", ")) : null)
                .build();
    }

    public List<SkillItemResponse> toSkillItemResponses(List<Skill> skills) {
        return skills.stream()
                .map(this::toSkillItemResponse)
                .toList();
    }

    public List<SkillItemResponse> toSelectedSkillResponses(List<StudentSkill> studentSkills) {
        return studentSkills.stream()
                .map(ss -> skillRepository.findById(ss.getSkill().getSkillId()).orElse(null))
                .filter(Objects::nonNull)
                .map(this::toSkillItemResponse)
                .toList();
    }

    public List<RequiredSkillResponse> toRequiredSkillResponses(List<CareerRequiredSkill> requiredSkills) {
        return requiredSkills.stream()
                .map(requiredSkill -> {
                    Skill skill = skillRepository.findById(requiredSkill.getSkill().getSkillId()).orElse(null);
                    return RequiredSkillResponse.builder()
                            .skill(skill != null ? toSkillItemResponse(skill) : null)
                            .importanceLevel(requiredSkill.getImportanceLevel())
                            .build();
                })
                .toList();
    }
}
