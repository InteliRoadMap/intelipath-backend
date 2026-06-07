package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.SkillNode;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.domain.entity.StudentProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StudentProgressRepository extends JpaRepository<StudentProgress, UUID> {

    List<StudentProgress> findByStudent(Student student);

    List<StudentProgress> findByStudent_StudentId(UUID studentId);

    List<StudentProgress> findByStudent_StudentIdAndSkillNode_NodeIdIn(UUID studentId, List<UUID> nodeIds);

    java.util.Optional<StudentProgress> findByStudentAndSkillNode(Student student, SkillNode skillNode);
}
