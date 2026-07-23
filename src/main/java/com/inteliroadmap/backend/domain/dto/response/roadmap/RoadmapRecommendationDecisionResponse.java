package com.inteliroadmap.backend.domain.dto.response.roadmap;

import com.inteliroadmap.backend.domain.enums.RecommendationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Result of accepting or rejecting a recommendation.
 * {@code roadmapProgress} is the recalculated overall roadmap percentage and
 * is only present after an accept; a reject never changes progress.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoadmapRecommendationDecisionResponse {

    private UUID recommendationId;
    private RecommendationStatus status;
    private LocalDateTime decidedAt;
    private Integer roadmapProgress;
}
