package com.inteliroadmap.backend.mappers;

import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.StudentSkill;
import com.inteliroadmap.backend.repositories.SkillRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillMapperTest {

    @Test
    void mapsStudentSkillToStableSkillDto() {
        Skill skill = Skill.builder()
                .skillId(UUID.randomUUID())
                .skillName("Java")
                .category("Backend")
                .build();
        StudentSkill studentSkill = StudentSkill.builder()
                .student(Student.builder().userId(UUID.randomUUID()).build())
                .skill(skill)
                .build();

        SkillRepository skillRepository = mock(SkillRepository.class);
        when(skillRepository.findById(skill.getSkillId())).thenReturn(Optional.of(skill));

        var result = new SkillMapper(skillRepository).toSelectedSkillResponses(List.of(studentSkill));

        assertEquals(skill.getSkillId(), result.getFirst().getSkillId());
        assertEquals("Java", result.getFirst().getSkillName());
    }
}
