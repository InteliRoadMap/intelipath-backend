package com.inteliroadmap.backend.domain.dto.response.course;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class CourseLessonResponse {
    private UUID lessonId;
    private String title;
    private String content;
    private String resourceUrl;
    private int orderIndex;
}
