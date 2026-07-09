package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.response.course.CourseResponse;
import com.inteliroadmap.backend.services.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Student-facing course catalogue: browse published courses, enroll, track progress. */
@RestController
@RequestMapping("/api/v1/courses")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
@Tag(name = "Courses", description = "Students browse and enroll in mentor courses")
public class CourseController {

    private final CourseService courseService;

    @GetMapping
    @Operation(summary = "Browse published courses (optionally filtered by career)")
    public ResponseEntity<List<CourseResponse>> browse(@RequestParam(required = false) UUID careerId) {
        return ResponseEntity.ok(courseService.browseCourses(careerId));
    }

    @GetMapping("/me/enrollments")
    @Operation(summary = "Courses the authenticated student is enrolled in")
    public ResponseEntity<List<CourseResponse>> myEnrollments() {
        return ResponseEntity.ok(courseService.getMyEnrollments());
    }

    @GetMapping("/{courseId}")
    @Operation(summary = "Course detail with lessons and enrollment state")
    public ResponseEntity<CourseResponse> detail(@PathVariable UUID courseId) {
        return ResponseEntity.ok(courseService.getCourseDetail(courseId));
    }

    @PostMapping("/{courseId}/enroll")
    @Operation(summary = "Enroll in a course")
    public ResponseEntity<CourseResponse> enroll(@PathVariable UUID courseId) {
        return ResponseEntity.ok(courseService.enroll(courseId));
    }

    @DeleteMapping("/{courseId}/enroll")
    @Operation(summary = "Leave a course")
    public ResponseEntity<Void> unenroll(@PathVariable UUID courseId) {
        courseService.unenroll(courseId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{courseId}/progress")
    @Operation(summary = "Update the student's progress (0-100)")
    public ResponseEntity<CourseResponse> updateProgress(@PathVariable UUID courseId,
                                                         @RequestParam int value) {
        return ResponseEntity.ok(courseService.updateProgress(courseId, value));
    }
}
