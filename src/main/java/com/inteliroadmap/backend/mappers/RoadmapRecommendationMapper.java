package com.inteliroadmap.backend.mappers;

import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapRecommendationItemResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapRecommendationResponse;
import com.inteliroadmap.backend.domain.entity.RoadmapRecommendation;
import com.inteliroadmap.backend.domain.entity.RoadmapRecommendationItem;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pure entity-to-DTO mapper for roadmap recommendations.
 * Node names are resolved by the caller and passed in, so this class never
 * touches a repository.
 */
@Component
public class RoadmapRecommendationMapper {

    public RoadmapRecommendationResponse toRecommendationResponse(
            RoadmapRecommendation recommendation,
            List<RoadmapRecommendationItem> items,
            Map<UUID, String> nodeNameByNodeId
    ) {
        List<RoadmapRecommendationItemResponse> itemResponses = items.stream()
                .map(item -> toItemResponse(item, nodeNameByNodeId.get(item.getNodeId())))
                .toList();

        return RoadmapRecommendationResponse.builder()
                .recommendationId(recommendation.getRecommendationId())
                .type(recommendation.getRecommendationType())
                .title(recommendation.getTitle())
                .summary(recommendation.getSummary())
                .reason(recommendation.getReason())
                .confidence(recommendation.getConfidence())
                .status(recommendation.getStatus())
                .createdAt(recommendation.getCreatedAt())
                .decidedAt(recommendation.getDecidedAt())
                .items(itemResponses)
                .build();
    }

    public RoadmapRecommendationItemResponse toItemResponse(RoadmapRecommendationItem item, String nodeName) {
        return RoadmapRecommendationItemResponse.builder()
                .recItemId(item.getRecItemId())
                .nodeId(item.getNodeId())
                .nodeName(nodeName)
                .action(item.getAction())
                .reason(item.getReason())
                .confidence(item.getConfidence())
                .evidenceIds(item.getEvidenceIds())
                .build();
    }
}
