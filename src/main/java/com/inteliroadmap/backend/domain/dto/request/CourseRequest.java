package com.inteliroadmap.backend.domain.dto.request;

import com.inteliroadmap.backend.domain.enums.CourseLevel;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** Payload a mentor sends to create or update a course (with its lessons). */
@Data
public class CourseRequest {

    @NotBlank(message = "Course title is required")
    private String title;

    private String description;

    private CourseLevel level;

    /** Optional career path this course supports. */
    private UUID careerId;

    private List<LessonRequest> lessons;

    @Data
    public static class LessonRequest {
        @NotBlank(message = "Lesson title is required")
        private String title;
        private String content;
        private String resourceUrl;
    }
}
