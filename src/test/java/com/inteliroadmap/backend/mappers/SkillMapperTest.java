package com.inteliroadmap.backend.mappers;

import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.StudentSkill;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkillMapperTest {

    @Test
    void mapsStudentSkillToStableSkillDto() {
        Skill skill = Skill.builder()
                .skillId(UUID.randomUUID())
                .skillName("Java")
                .category("Backend")
                .career("Software Developer")
                .build();
        StudentSkill studentSkill = StudentSkill.builder()
                .student(Student.builder().userId(UUID.randomUUID()).build())
                .skill(skill)
                .build();

        var result = new SkillMapper().toSelectedSkillResponses(List.of(studentSkill));

        assertEquals(skill.getSkillId(), result.getFirst().getSkillId());
        assertEquals("Java", result.getFirst().getSkillName());
    }
}
