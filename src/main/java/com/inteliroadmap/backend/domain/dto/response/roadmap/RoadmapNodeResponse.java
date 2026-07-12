package com.inteliroadmap.backend.domain.dto.response.roadmap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoadmapNodeResponse {
    private UUID nodeId;
    private String nodeName;
    private String parentNode;
    private String previousNode;
    private Integer nodeLevel;
    private String stage;
    private String completionPolicy;
    private Integer weight;
    private Integer requiredProficiency;
    // Topic (spine) node that owns child sub-skills and auto-completes from them.
    private Boolean parentTopic;
    private Integer childTotal;
    private Integer childCompleted;
    // Layout metadata (presentation only, sourced from roadmap_node_layouts).
    private Double positionX;
    private Double positionY;
    private String lane;
    private Integer displayOrder;
    private Integer layoutVersion;
    private String description;
    private Object resource;
    private String status;
}
