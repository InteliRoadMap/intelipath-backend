package com.inteliroadmap.backend.domain.dto.response.mentor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MentorPendingReviewResponse {
    private String id;
    private String studentId;
    private String portfolioSlug;
    private String studentName;
    private String yob;
    private String targetCareer;
    private String university;
}
