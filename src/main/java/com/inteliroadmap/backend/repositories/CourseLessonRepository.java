package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.CourseLesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CourseLessonRepository extends JpaRepository<CourseLesson, UUID> {

    List<CourseLesson> findByCourseIdOrderByOrderIndexAsc(UUID courseId);

    long countByCourseId(UUID courseId);

    void deleteByCourseId(UUID courseId);
}
