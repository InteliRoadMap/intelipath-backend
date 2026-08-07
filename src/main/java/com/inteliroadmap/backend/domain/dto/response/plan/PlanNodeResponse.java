package com.inteliroadmap.backend.domain.dto.response.plan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** A roadmap node offered as the material for a plan step. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanNodeResponse {

    private UUID nodeId;
    private String nodeName;
    private String description;

    /** completed | in_progress | current | locked, from the roadmap's own gating. */
    private String status;

    /** Curated links; FR2.3 requires two on a technical node. */
    private List<String> resources;
}
