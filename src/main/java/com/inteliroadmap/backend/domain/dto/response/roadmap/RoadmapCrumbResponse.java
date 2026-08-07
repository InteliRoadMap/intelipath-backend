package com.inteliroadmap.backend.domain.dto.response.roadmap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** One step of the trail back out of a sub-roadmap. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoadmapCrumbResponse {

    /**
     * Null for the career itself, which is not a node — the client sends no id
     * to go back to the career roadmap.
     */
    private UUID nodeId;

    private String name;
}
