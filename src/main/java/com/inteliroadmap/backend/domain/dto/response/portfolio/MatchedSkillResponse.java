package com.inteliroadmap.backend.domain.dto.response.portfolio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MatchedSkillResponse {
    private String skill;
    /** What the model claimed, 0..1, before the evidence layer clamped it. */
    private double confidence;
    /**
     * Live evidence status: ACCEPTED, REJECTED, PENDING — or NOT_RECORDED when the
     * model named a skill that is not in the catalog, which the evidence layer drops
     * rather than minting a new skill from a model's guess.
     */
    private String status;
}
