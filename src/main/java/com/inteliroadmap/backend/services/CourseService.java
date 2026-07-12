package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.CourseRequest;
import com.inteliroadmap.backend.domain.dto.response.course.CourseResponse;

import java.util.List;
import java.util.UUID;

/**
 * Mentor-authored courses that students can opt into.
 * Mentor operations act on the authenticated mentor's own courses; student
 * operations act on the authenticated student.
 */
public interface CourseService {

    // ---- Mentor ----
    CourseResponse createCourse(CourseRequest request);

    CourseResponse updateCourse(UUID courseId, CourseRequest request);

    void deleteCourse(UUID courseId);

    CourseResponse setPublished(UUID courseId, boolean published);

    List<CourseResponse> getMentorCourses();

    // ---- Student ----
    List<CourseResponse> browseCourses(UUID careerId, UUID nodeId);

    CourseResponse getCourseDetail(UUID courseId);

    CourseResponse enroll(UUID courseId);

    void unenroll(UUID courseId);

    CourseResponse updateProgress(UUID courseId, int progress);

    List<CourseResponse> getMyEnrollments();
}
