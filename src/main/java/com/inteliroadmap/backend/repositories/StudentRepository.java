package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.projection.StudentInfoProjection;
import com.inteliroadmap.backend.domain.entity.CareerRole;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.enums.AccountType;
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
    List<Student> findByCareerRoleAndUniversityName(CareerRole career, String universityName);
    List<Student> findByUniversityName(String universityName);

    @Query("SELECT DISTINCT c.careerName FROM Student s JOIN CareerRole c ON s.careerRole.careerId = c.careerId WHERE s.universityName = :universityName")
    List<String> findDistinctCareerNamesByUniversityName(@Param("universityName") String universityName);

    // The accountType predicate is the counselor tenant boundary: it must stay identical in
    // the main query and countQuery, or the list and its page count disagree.
    @Query(value = "SELECT s.userId as studentId, u.fullName as fullName, u.email as email, s.universityName as university, c.careerName as careerName " +
           "FROM Student s " +
           "JOIN User u ON s.userId = u.userId " +
           "LEFT JOIN CareerRole c ON s.careerRole.careerId = c.careerId " +
           "WHERE (LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND u.accountType = :accountType " +
           "AND (:careerName IS NULL OR :careerName = '' OR c.careerName = :careerName) " +
           "AND u.role = com.inteliroadmap.backend.domain.enums.UserRole.STUDENT " +
           "ORDER BY u.createdAt DESC",
           countQuery = "SELECT COUNT(s) " +
           "FROM Student s " +
           "JOIN User u ON s.userId = u.userId " +
           "LEFT JOIN CareerRole c ON s.careerRole.careerId = c.careerId " +
           "WHERE (LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND u.accountType = :accountType " +
           "AND (:careerName IS NULL OR :careerName = '' OR c.careerName = :careerName) " +
           "AND u.role = com.inteliroadmap.backend.domain.enums.UserRole.STUDENT")
    Page<StudentInfoProjection> findStudentInfos(@Param("search") String search, @Param("careerName") String careerName, @Param("accountType") AccountType accountType, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select student from Student student where student.userId = :userId")
    Optional<Student> findByIdForUpdate(@Param("userId") UUID userId);

    // Career id fetched as a scalar so callers on non-request threads (AI tool
    // executors) never touch the LAZY careerRole proxy without a session.
    @Query("select s.careerRole.careerId from Student s where s.userId = :userId")
    Optional<UUID> findCareerIdByUserId(@Param("userId") UUID userId);

    boolean existsByPortfolioSlug(String portfolioSlug);
    Optional<Student> findByPortfolioSlug(String portfolioSlug);

    List<Student> findByPortfolioSlugIsNullOrPortfolioSlugEquals(String portfolioSlug);
}
