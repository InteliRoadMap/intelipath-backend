package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.StudentSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StudentSkillRepository extends JpaRepository<StudentSkill, UUID> {
    List<StudentSkill> findByStudent(Student student);

    List<StudentSkill> findByStudent_StudentId(UUID studentId);

    List<StudentSkill> findByStudentAndSkill_SkillIdIn(Student student, List<UUID> skillIds);
}
