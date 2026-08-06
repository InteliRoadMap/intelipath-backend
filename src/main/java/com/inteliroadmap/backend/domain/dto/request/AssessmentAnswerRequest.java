package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** One skill's answer in a submitted self-assessment. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentAnswerRequest {

    @NotNull(message = "skillId is required")
    private UUID skillId;

    /**
     * NONE | AWARE | PRACTICED | APPLIED | PROFESSIONAL.
     *
     * <p>Typed as a String and parsed in the service rather than bound straight
     * to the enum, so an unrecognised value produces a message naming the field
     * and the allowed tokens instead of Jackson's deserialisation error.
     */
    @NotBlank(message = "level is required")
    private String level;

    /**
     * What the student built with this skill. Required for APPLIED and
     * PROFESSIONAL claims on the highest-importance skills — the assessment is
     * only meaningfully "AI-scored" because there is prose here to judge.
     */
    @Size(max = 500, message = "note must be at most 500 characters")
    private String note;
}
