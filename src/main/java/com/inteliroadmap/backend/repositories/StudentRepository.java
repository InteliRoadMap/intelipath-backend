package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.CareerRole;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.mappers.DatasetMapper;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StudentRepository extends JpaRepository<Student, UUID> {
    Student findByStudentId(UUID studentId);
    List<Student> findByCareerRole(CareerRole careerRole);
    Student findByUser(User user);

    @Query(value = 
        "SELECT " +
        "    st.student_id AS studentId, " +
        "    u.full_name AS fullName, " +
        "    s.skill_id AS skillId, " +
        "    s.skill_name AS skillName, " +
        "    crs.importance_level AS importanceLevel " +
        "FROM students st " +
        "JOIN users u ON st.user_id = u.user_id " +
        "JOIN career_roles cr ON cr.career_name = :careerName " +
        "JOIN career_required_skills crs ON crs.career_id = cr.career_id " +
        "JOIN skills s ON crs.skill_id = s.skill_id " +
        "LEFT JOIN student_skills ss ON ss.student_id = st.student_id AND ss.skill_id = crs.skill_id " +
        "WHERE ss.skill_id IS NULL " +
        "ORDER BY st.student_id, s.skill_name", 
        nativeQuery = true)
    List<DatasetMapper> findMissingSkillsByCareerName(@Param("careerName") String careerName);

    @Query(value = 
        "SELECT " +
        "    st.student_id AS studentId, " +
        "    u.full_name AS fullName, " +
        "    s.skill_id AS skillId, " +
        "    s.skill_name AS skillName, " +
        "    crs.importance_level AS importanceLevel " +
        "FROM students st " +
        "JOIN users u ON st.user_id = u.user_id " +
        "JOIN career_roles cr ON cr.career_name = :careerName " +
        "JOIN career_required_skills crs ON crs.career_id = cr.career_id " +
        "JOIN skills s ON crs.skill_id = s.skill_id " +
        "LEFT JOIN student_skills ss ON ss.student_id = st.student_id AND ss.skill_id = crs.skill_id " +
        "WHERE ss.skill_id IS NULL AND st.student_id = :studentId " +
        "ORDER BY s.skill_name", 
        nativeQuery = true)
    List<DatasetMapper> findMissingSkillsByStudentIdAndCareerName(@Param("studentId") UUID studentId, @Param("careerName") String careerName);
}
