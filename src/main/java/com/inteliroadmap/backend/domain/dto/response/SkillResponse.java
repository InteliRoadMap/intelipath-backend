package com.inteliroadmap.backend.domain.dto.response;

import com.inteliroadmap.backend.domain.entity.CareerRequiredSkill;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.entity.StudentSkill;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SkillResponse {
    private List<StudentSkill> studentSkills;
    private List<CareerRequiredSkill> careerRequiredSkills;
    private List<Skill> skills;
}
