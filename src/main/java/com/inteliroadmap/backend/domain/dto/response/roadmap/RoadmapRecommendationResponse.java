package com.inteliroadmap.backend.domain.dto.response.roadmap;

import com.inteliroadmap.backend.domain.enums.RecommendationStatus;
import com.inteliroadmap.backend.domain.enums.RecommendationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A roadmap personalization proposal shown to the student, together with the
 * concrete node-level actions it would apply once accepted.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoadmapRecommendationResponse {

    private UUID recommendationId;
    private RecommendationType type;
    private String title;
    private String summary;
    private String reason;
    private BigDecimal confidence;
    private RecommendationStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime decidedAt;
    private List<RoadmapRecommendationItemResponse> items;
}
