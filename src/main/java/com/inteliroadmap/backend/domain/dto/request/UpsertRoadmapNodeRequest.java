package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** Create/update payload for a roadmap node in the mentor editor. */
@Data
public class UpsertRoadmapNodeRequest {

    @NotBlank(message = "Node name is required")
    private String nodeName;

    private String description;

    /** One of FOUNDATION, CORE, PRACTICAL, ADVANCED, JOB_READY. */
    private String stage;

    /** One of NEVER_COMPLETE, MANUAL_ONLY, EVIDENCE_ALLOWED. */
    private String completionPolicy;

    private Integer weight;

    private Integer requiredProficiency;

    private UUID parentNodeId;

    private UUID previousNodeId;

    /** Resource links shown on the node's detail panel. */
    private List<String> resources;

    // Optional initial layout placement; nodes are usually arranged by dragging.
    private Double positionX;

    private Double positionY;

    private String lane;

    private Integer displayOrder;
}
