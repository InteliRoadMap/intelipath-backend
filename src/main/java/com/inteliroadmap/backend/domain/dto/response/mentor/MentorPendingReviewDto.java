package com.inteliroadmap.backend.domain.dto.response.mentor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MentorPendingReviewDto {
    private String id;
    private String initials;
    private String name;
    private String status;
    private String major;
    private String year;
    private String role;
    private int progress;
}
