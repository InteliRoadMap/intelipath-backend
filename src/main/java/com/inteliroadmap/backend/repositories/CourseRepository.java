package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.Course;
import com.inteliroadmap.backend.domain.enums.CourseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CourseRepository extends JpaRepository<Course, UUID> {

    List<Course> findByMentorIdOrderByUpdatedAtDesc(UUID mentorId);

    List<Course> findByStatusOrderByCreatedAtDesc(CourseStatus status);

    List<Course> findByStatusAndCareerIdOrderByCreatedAtDesc(CourseStatus status, UUID careerId);

    List<Course> findByStatusAndNodeIdOrderByCreatedAtDesc(CourseStatus status, UUID nodeId);
}
