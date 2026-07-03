package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.dto.response.StudentInfoProjection;
import com.inteliroadmap.backend.domain.entity.CareerRole;
import com.inteliroadmap.backend.domain.entity.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface StudentRepository extends JpaRepository<Student, UUID> {
    Student findByUserId(UUID userId);
    List<Student> findByCareerRole(CareerRole career);

    @Query("SELECT s.userId as studentId, u.fullName as fullName, u.email as email, COALESCE(s.universityName, s.university.name) as university, c.careerName as careerName " +
           "FROM Student s " +
           "JOIN User u ON s.userId = u.userId " +
           "LEFT JOIN CareerRole c ON s.careerRole.careerId = c.careerId " +
           "WHERE (LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND s.university.universityId = :universityId " +
           "AND u.role = UserRole.STUDENT")
    Page<StudentInfoProjection> findStudentInfos(@Param("search") String search, @Param("universityId") UUID universityId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select student from Student student where student.userId = :userId")
    Optional<Student> findByIdForUpdate(@Param("userId") UUID userId);

    boolean existsByPortfolioSlug(String portfolioSlug);
    Optional<Student> findByPortfolioSlug(String portfolioSlug);
}
