package com.inteliroadmap.backend.domain.dto.response.mentor;

import com.inteliroadmap.backend.domain.dto.response.mentor.MentorDashboardMetricsResponse;
import com.inteliroadmap.backend.domain.entity.Feedback;
import com.inteliroadmap.backend.domain.entity.PortfolioReviewRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MentorResponse {
    private MentorDashboardMetricsResponse metrics;
    private Page<PortfolioReviewRequest> pendingReviews;
    private Page<Map<String, Object>> students;
    private Feedback feedback;
    private String insight;
    private String welcomeAlert;
}
