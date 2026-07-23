package com.inteliroadmap.backend.domain.dto.response.roadmap;

import com.inteliroadmap.backend.domain.enums.RecommendationAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One concrete roadmap change proposed inside a recommendation,
 * e.g. "mark node X as completed".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoadmapRecommendationItemResponse {

    private UUID recItemId;
    private UUID nodeId;
    private String nodeName;
    private RecommendationAction action;
    private String reason;
    private BigDecimal confidence;
    private List<UUID> evidenceIds;
}
