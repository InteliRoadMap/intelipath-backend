package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.StudentSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentSkillRepository extends JpaRepository<StudentSkill, UUID> {
    List<StudentSkill> findByStudent_UserId(UUID studentId);
    Optional<StudentSkill> findByStudent_UserIdAndSkill_SkillId(UUID studentId, UUID skillId);
    List<StudentSkill> findByStudent_UserIdAndSkill_SkillIdIn(UUID studentId, List<UUID> skillIds);
    boolean existsByStudent_UserIdAndSkill_SkillId(UUID studentId, UUID skillId);

    @Query(value = "SELECT s.skill_name as skillName, COUNT(st.user_id) as count FROM students st JOIN career_roles cr ON st.career_id = cr.career_id JOIN career_required_skills crs ON crs.career_id = cr.career_id JOIN skills s ON crs.skill_id = s.skill_id LEFT JOIN student_skills ss ON ss.user_id = st.user_id AND ss.skill_id = crs.skill_id WHERE ss.skill_id IS NULL AND cr.career_id = :careerId GROUP BY s.skill_name", nativeQuery = true)
    List<Object[]> findMissingSkillsByCareerId(@Param("careerId") UUID careerId);

    @Query(value = "SELECT s.skill_name FROM students st JOIN career_roles cr ON st.career_id = cr.career_id JOIN career_required_skills crs ON crs.career_id = cr.career_id JOIN skills s ON crs.skill_id = s.skill_id LEFT JOIN student_skills ss ON ss.user_id = st.user_id AND ss.skill_id = crs.skill_id WHERE ss.skill_id IS NULL AND st.user_id = :userId AND cr.career_id = :careerId ORDER BY s.skill_name", nativeQuery = true)
    List<String> findMissingSkillsByStudentIdAndCareerId(@Param("userId") UUID userId, @Param("careerId") UUID careerId);

    List<String> findSkillNamesByStudent_UserId(UUID studentId);

    @Query("SELECT ss.student.userId, ss.skill.skillName FROM StudentSkill ss WHERE ss.student.userId IN :studentIds")
    List<Object[]> findSkillNamesByStudentIds(@Param("studentIds") List<UUID> studentIds);
}
