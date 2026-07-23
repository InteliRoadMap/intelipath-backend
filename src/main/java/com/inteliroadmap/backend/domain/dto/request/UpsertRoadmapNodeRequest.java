package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** Create/update payload for a roadmap node in the mentor editor. */
@Data
public class UpsertRoadmapNodeRequest {

    @NotBlank(message = "Node name is required")
    @Size(max = 200, message = "Node name must not exceed 200 characters")
    private String nodeName;

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    private String description;

    /** One of FOUNDATION, CORE, PRACTICAL, ADVANCED, JOB_READY. */
    @Size(max = 30, message = "Stage must not exceed 30 characters")
    private String stage;

    /** One of NEVER_COMPLETE, MANUAL_ONLY, EVIDENCE_ALLOWED. */
    @Size(max = 30, message = "Completion policy must not exceed 30 characters")
    private String completionPolicy;

    @PositiveOrZero(message = "Weight must be zero or positive")
    @Max(value = 1000, message = "Weight must not exceed 1000")
    private Integer weight;

    @Min(value = 0, message = "Required proficiency must be between 0 and 100")
    @Max(value = 100, message = "Required proficiency must be between 0 and 100")
    private Integer requiredProficiency;

    private UUID parentNodeId;

    private UUID previousNodeId;

    /** Resource links shown on the node's detail panel. */
    private List<@Size(max = 2048, message = "Resource URL must not exceed 2048 characters") String> resources;

    // Optional initial layout placement; nodes are usually arranged by dragging.
    private Double positionX;

    private Double positionY;

    @Size(max = 50, message = "Lane must not exceed 50 characters")
    private String lane;

    @PositiveOrZero(message = "Display order must be zero or positive")
    private Integer displayOrder;
}
