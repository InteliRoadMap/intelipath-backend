package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.AcademicCounselor;
import com.inteliroadmap.backend.domain.entity.CareerRole;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.mappers.DatasetMapper;
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
    List<Student> findByCareerRoleAndUniversity_UniversityId(CareerRole careerRole, UUID universityId);

    @Query("SELECT s FROM Student s JOIN User u ON s.userId = u.userId WHERE (LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.careerRole.careerName) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<Student> searchStudentsInfo(@Param("search") String search);

    List<Student> findByUniversity_UniversityId(UUID universityId);

    @Query("SELECT s FROM Student s JOIN User u ON s.userId = u.userId WHERE s.university.universityId = :universityId AND (LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.careerRole.careerName) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<Student> searchStudentsInfoByUniversity(@Param("search") String search, @Param("universityId") UUID universityId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select student from Student student where student.userId = :userId")
    Optional<Student> findByIdForUpdate(@Param("userId") UUID userId);

    boolean existsByPortfolioSlug(String portfolioSlug);
    Optional<Student> findByPortfolioSlug(String portfolioSlug);
}
