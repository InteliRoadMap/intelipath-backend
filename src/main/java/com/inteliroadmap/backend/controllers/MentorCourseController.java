package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.request.CourseRequest;
import com.inteliroadmap.backend.domain.dto.response.course.CourseResponse;
import com.inteliroadmap.backend.services.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Mentor-facing course authoring: create, edit, publish and manage own courses. */
@RestController
@RequestMapping("/api/v1/mentor/courses")
@PreAuthorize("hasRole('MENTOR')")
@RequiredArgsConstructor
@Tag(name = "Mentor Courses", description = "Mentors author learning courses for students")
public class MentorCourseController {

    private final CourseService courseService;

    @GetMapping
    @Operation(summary = "List the authenticated mentor's courses")
    public ResponseEntity<List<CourseResponse>> myCourses() {
        return ResponseEntity.ok(courseService.getMentorCourses());
    }

    @PostMapping
    @Operation(summary = "Create a new (draft) course")
    public ResponseEntity<CourseResponse> create(@Valid @RequestBody CourseRequest request) {
        return ResponseEntity.ok(courseService.createCourse(request));
    }

    @PutMapping("/{courseId}")
    @Operation(summary = "Update a course and replace its lessons")
    public ResponseEntity<CourseResponse> update(@PathVariable UUID courseId,
                                                 @Valid @RequestBody CourseRequest request) {
        return ResponseEntity.ok(courseService.updateCourse(courseId, request));
    }

    @DeleteMapping("/{courseId}")
    @Operation(summary = "Delete a course")
    public ResponseEntity<Void> delete(@PathVariable UUID courseId) {
        courseService.deleteCourse(courseId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{courseId}/publish")
    @Operation(summary = "Publish or unpublish a course")
    public ResponseEntity<CourseResponse> setPublished(@PathVariable UUID courseId,
                                                       @RequestParam(defaultValue = "true") boolean published) {
        return ResponseEntity.ok(courseService.setPublished(courseId, published));
    }
}
