package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.CareerRole;
import com.inteliroadmap.backend.domain.entity.Student;
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
    List<Student> findByCareerRole(CareerRole careerRole);

    @Query("SELECT s FROM Student s, User u WHERE s.userId = u.userId AND (LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))) AND s.university = :uni AND u.role = UserRole.STUDENT")
    List<Student> searchStudentsInfo(@Param("search") String search, @Param("uni") String uni);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select student from Student student where student.userId = :userId")
    Optional<Student> findByIdForUpdate(@Param("userId") UUID userId);

    boolean existsByPortfolioSlug(String portfolioSlug);
    Optional<Student> findByPortfolioSlug(String portfolioSlug);
}
