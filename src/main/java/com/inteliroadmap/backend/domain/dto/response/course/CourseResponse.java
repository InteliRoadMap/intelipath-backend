package com.inteliroadmap.backend.domain.dto.response.course;

import com.inteliroadmap.backend.domain.enums.CourseLevel;
import com.inteliroadmap.backend.domain.enums.CourseStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CourseResponse {
    private UUID courseId;
    private String title;
    private String description;
    private CourseLevel level;
    private CourseStatus status;

    private UUID mentorId;
    private String mentorName;
    private UUID careerId;
    private String careerName;
    private UUID nodeId;
    private String nodeName;

    private long lessonCount;
    private long enrolledCount;
    private LocalDateTime createdAt;

    /** Populated in the detail view. */
    private List<CourseLessonResponse> lessons;

    /** Student context: whether the current student is enrolled, and their progress. */
    private boolean enrolled;
    private Integer progress;
}
