package com.inteliroadmap.backend.domain.dto.response.mentor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MentorDashboardMetricsResponse {
    private String responseTime;
    private long mentees;
    private long pendingReviews;
    private long feedbacks;
}
