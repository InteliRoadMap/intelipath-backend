package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.CourseEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollment, UUID> {

    List<CourseEnrollment> findByStudentIdOrderByEnrolledAtDesc(UUID studentId);

    Optional<CourseEnrollment> findByCourseIdAndStudentId(UUID courseId, UUID studentId);

    boolean existsByCourseIdAndStudentId(UUID courseId, UUID studentId);

    long countByCourseId(UUID courseId);
}
