package com.inteliroadmap.backend.domain.dto.response.portfolio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Aggregated progress for one roadmap stage; no learning resources are exposed. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoadmapStageResponse {
    private String name;
    private Integer totalNodes;
    private Integer completedNodes;
    private Integer currentNodes;
}
