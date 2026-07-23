package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.SkillNode;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.StudentProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StudentProgressRepository extends JpaRepository<StudentProgress, UUID> {
    List<StudentProgress> findByStudent_UserId(UUID studentId);
    List<StudentProgress> findByStudent_UserIdAndSkillNode_NodeIdIn(UUID studentId, List<UUID> nodeIds);
    java.util.Optional<StudentProgress> findByStudent_UserIdAndSkillNode_NodeId(UUID studentId, UUID nodeId);
    boolean existsBySkillNode_NodeId(UUID nodeId);

    @Query(value = "SELECT COUNT(*) AS total_completed FROM student_progress sp JOIN skill_nodes sn ON sp.node_id = sn.node_id WHERE sn.career_id = :careerId AND sp.user_id = :studentId AND sp.status = 'COMPLETED'", nativeQuery = true)
    int findRoadmapTotalNodeCompletedByStudentIdAndCareerId(@Param("studentId") UUID studentId, @Param("careerId") UUID careerId);

}
