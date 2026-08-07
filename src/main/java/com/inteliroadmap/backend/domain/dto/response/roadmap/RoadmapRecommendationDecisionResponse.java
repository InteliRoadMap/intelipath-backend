package com.inteliroadmap.backend.domain.dto.response.roadmap;

import com.inteliroadmap.backend.domain.enums.RecommendationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
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

    /**
     * Nodes this acceptance actually marked complete.
     *
     * <p>Not the item count: only MARK_COMPLETE items write progress, and only
     * those that survived gating. The assessment stored the item count as
     * `applied_node_count` and told the student "12 nodes marked" when the real
     * answer could be zero.
     */
    private Integer completedNodeCount;

    /**
     * Which nodes those were.
     *
     * <p>The count alone tells the student a number; the ids let the roadmap show
     * them the marking happen. Without this the canvas can only refetch and
     * silently render a different set of ticks, which is indistinguishable from
     * nothing having occurred — the student supplied their skills and the page
     * looked the same.
     *
     * <p>Additive and nullable: a client that does not read it behaves exactly as
     * before.
     */
    private List<UUID> completedNodeIds;
}
