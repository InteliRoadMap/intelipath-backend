package com.inteliroadmap.backend.domain.dto.response.student;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** The outcome of a sat paper. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradedAssessmentResultResponse {
    private UUID assessmentId;
    /** BEGINNER | FRESHER | JUNIOR | MID | SENIOR | EXPERT. */
    private String level;
    /** The level the multiple-choice half alone supports, before the rubric moved it. */
    private String objectiveLevel;
    /** 0..1 over the auto-graded items. */
    private BigDecimal objectiveScore;
    /** 0..1 over the rubric items, or null when the model could not be reached. */
    private BigDecimal rubricScore;
    /** Highest tier answered right more often than not; what the roadmap unlocks. */
    private int tierReach;
    private String rationale;
    /** How many catalog skills the paper produced evidence for. */
    private int evidencedSkillCount;
    /** Roadmap nodes marked as covered by what the paper evidenced. */
    private int appliedNodeCount;
    /**
     * Which nodes those were, so the roadmap can animate them being marked.
     *
     * <p>A count in a result panel is a claim the student has to go and verify;
     * the ids let the canvas show the ticks landing on the way back.
     */
    @Builder.Default
    private List<UUID> markedNodeIds = List.of();
    @Builder.Default
    private List<GradedItemResultResponse> items = List.of();
}
