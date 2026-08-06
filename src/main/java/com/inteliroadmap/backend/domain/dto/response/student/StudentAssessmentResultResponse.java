package com.inteliroadmap.backend.domain.dto.response.student;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** The outcome of one assessment run. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentAssessmentResultResponse {

    private UUID assessmentId;
    private UUID careerId;
    private String careerName;

    /** FRESHER | JUNIOR | MID — the level the student is told. */
    private String level;

    /**
     * What the model alone concluded, before the formula and the ceiling.
     * Exposed so the level never looks like it came from nowhere.
     */
    private String rawLevel;

    /** Two or three sentences addressed to the student, from the model. */
    private String rationale;

    /** Share of the career's required skills the student is at APPLIED or above on. */
    private Double coverage;

    /**
     * How many roadmap nodes this run marked as already covered. Zero is a
     * normal, honest outcome: nothing the student claimed cleared the evidence
     * bar for its node.
     */
    @Builder.Default
    private Integer appliedNodeCount = 0;

    @Builder.Default
    private List<AssessedSkillResponse> assessedSkills = List.of();

    /** ISO-8601 instant the scoring finished. */
    private String computedAt;
}
