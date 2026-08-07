package com.inteliroadmap.backend.domain.dto.response.student;

import com.inteliroadmap.backend.domain.enums.RoadmapStepStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoadmapStepResponse {
    private UUID id;
    private String title;
    private RoadmapStepStatus status;

    /**
     * The node this step sits under, or null for a root.
     *
     * A title alone is not always a task: "$eq" means nothing until you know it is
     * a MongoDB comparison operator, and the dashboard was showing exactly that.
     */
    private String parentTitle;

    /** How deep in the tree, so a caller can tell a track apart from a leaf detail. */
    private Short depth;
}
