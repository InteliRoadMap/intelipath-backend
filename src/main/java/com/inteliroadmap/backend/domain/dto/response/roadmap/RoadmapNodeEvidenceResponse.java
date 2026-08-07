package com.inteliroadmap.backend.domain.dto.response.roadmap;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/** One auditable fact considered for a roadmap node. */
@Data
@Builder
public class RoadmapNodeEvidenceResponse {
    private UUID evidenceId;
    private String skillName;
    private String sourceType;
    private String sourceUrl;
    private Double confidence;
    private String status;
    private String detectedBy;
}
