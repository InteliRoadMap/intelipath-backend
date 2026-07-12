package com.inteliroadmap.backend.domain.dto.request;

import com.inteliroadmap.backend.domain.enums.CourseLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** Payload a mentor sends to create or update a course (with its lessons). */
@Data
public class CourseRequest {

    @NotBlank(message = "Course title is required")
    @Size(max = 200, message = "Course title must not exceed 200 characters")
    private String title;

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    private String description;

    private CourseLevel level;

    /** The career path this course supports (required). */
    @NotNull(message = "A career is required for the course")
    private UUID careerId;

    /** Optional specific roadmap node this course targets within the career. */
    private UUID nodeId;

    @Valid
    private List<LessonRequest> lessons;

    @Data
    public static class LessonRequest {
        @NotBlank(message = "Lesson title is required")
        @Size(max = 200, message = "Lesson title must not exceed 200 characters")
        private String title;

        @Size(max = 20000, message = "Lesson content must not exceed 20000 characters")
        private String content;

        @Size(max = 2048, message = "Resource URL must not exceed 2048 characters")
        private String resourceUrl;
    }
}
