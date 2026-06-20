package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.SkillNode;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.domain.entity.StudentProgress;
import com.inteliroadmap.backend.mappers.DatasetMapper;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StudentProgressRepository extends JpaRepository<StudentProgress, UUID> {

    List<StudentProgress> findByStudent(Student student);

    List<StudentProgress> findByStudent_UserId(UUID userId);

    List<StudentProgress> findByStudent_UserIdAndSkillNode_NodeIdIn(UUID userId, List<UUID> nodeIds);

    java.util.Optional<StudentProgress> findByStudentAndSkillNode(Student student, SkillNode skillNode);

    @Query(value = "SELECT COUNT(*) AS total_completed " +
            "FROM student_progress sp " +
            "JOIN skill_nodes sn " +
            "ON sp.node_id = sn.node_id " +
            "WHERE sn.career_id = :careerId " +
            "AND sp.student_id = :studentId " +
            "AND sp.status = 'COMPLETED';", nativeQuery = true)
    int findRoadmapTotalNodeCompletedByCareerIdAndStudentId(@Param("careerId") UUID careerId, @Param("studentId") UUID studentId);

    // Keep the old method for backward compatibility (used in places where studentId is not needed)
    @Query(value = "SELECT COUNT(*) AS total_completed " +
            "FROM student_progress sp " +
            "JOIN skill_nodes sn " +
            "ON sp.node_id = sn.node_id " +
            "WHERE sn.career_id = :careerId " +
            "AND sp.status = 'COMPLETED';", nativeQuery = true)
    int findRoadmapTotalNodeCompletedByCareerId(@Param("careerId") UUID careerId);

}
