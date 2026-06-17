package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.StudentSkill;
import com.inteliroadmap.backend.mappers.DatasetMapper;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StudentSkillRepository extends JpaRepository<StudentSkill, UUID> {
    List<StudentSkill> findByStudent(Student student);

    List<StudentSkill> findByStudent_UserId(UUID userId);

    List<StudentSkill> findByStudentAndSkill_SkillIdIn(Student student, List<UUID> skillIds);

    boolean existsByStudentAndSkill(Student student, com.inteliroadmap.backend.domain.entity.Skill skill);

    @Query(value = "SELECT " +
            "CAST(st.user_id AS VARCHAR) as studentId, " +
            "u.full_name as fullName, " +
            "CAST(s.skill_id AS VARCHAR) as skillId, " +
            "s.skill_name as skillName, " +
            "crs.importance_level as importanceLevel " +
            "FROM students st " +
            "JOIN users u ON st.user_id = u.user_id " +
            "JOIN career_roles cr ON st.career_id = cr.career_id " +
            "JOIN career_required_skills crs ON crs.career_id = cr.career_id " +
            "JOIN skills s ON crs.skill_id = s.skill_id " +
            "LEFT JOIN student_skills ss ON ss.user_id = st.user_id AND ss.skill_id = crs.skill_id " +
            "WHERE ss.skill_id IS NULL " +
            "ORDER BY st.user_id, s.skill_name", nativeQuery = true)
    List<DatasetMapper> findAllMissingSkills();

    @Query(value = "SELECT " +
            "CAST(st.user_id AS VARCHAR) as studentId, " +
            "u.full_name as fullName, " +
            "CAST(s.skill_id AS VARCHAR) as skillId, " +
            "s.skill_name as skillName, " +
            "crs.importance_level as importanceLevel " +
            "FROM students st " +
            "JOIN users u ON st.user_id = u.user_id " +
            "JOIN career_roles cr ON st.career_id = cr.career_id " +
            "JOIN career_required_skills crs ON crs.career_id = cr.career_id " +
            "JOIN skills s ON crs.skill_id = s.skill_id " +
            "LEFT JOIN student_skills ss ON ss.user_id = st.user_id AND ss.skill_id = crs.skill_id " +
            "WHERE ss.skill_id IS NULL AND cr.career_id = :careerId " +
            "ORDER BY st.user_id, s.skill_name", nativeQuery = true)
    List<DatasetMapper> findMissingSkillsByCareerId(@Param("careerId") UUID careerId);

    @Query(value = "SELECT " +
            "CAST(st.user_id AS VARCHAR) as studentId, " +
            "u.full_name as fullName, " +
            "CAST(s.skill_id AS VARCHAR) as skillId, " +
            "s.skill_name as skillName, " +
            "crs.importance_level as importanceLevel " +
            "FROM students st " +
            "JOIN users u ON st.user_id = u.user_id " +
            "JOIN career_roles cr ON st.career_id = cr.career_id " +
            "JOIN career_required_skills crs ON crs.career_id = cr.career_id " +
            "JOIN skills s ON crs.skill_id = s.skill_id " +
            "LEFT JOIN student_skills ss ON ss.user_id = st.user_id AND ss.skill_id = crs.skill_id " +
            "WHERE ss.skill_id IS NULL AND st.user_id = :userId AND cr.career_name = :careerName " +
            "ORDER BY s.skill_name", nativeQuery = true)
    List<DatasetMapper> findMissingSkillsByStudentIdAndCareerName(@Param("userId") UUID userId, @Param("careerName") String careerName);

}
