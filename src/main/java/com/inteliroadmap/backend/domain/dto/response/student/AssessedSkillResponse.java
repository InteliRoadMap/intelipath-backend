package com.inteliroadmap.backend.domain.dto.response.student;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** What the AI concluded about one skill, next to what the student claimed. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessedSkillResponse {

    private UUID skillId;
    private String skillName;

    /** AWARE | PRACTICED | APPLIED | PROFESSIONAL, as the student answered. */
    private String declaredLevel;

    /**
     * The same scale after the model's judgement. Lower than
     * {@link #declaredLevel} when a claim was not supported by its note — shown
     * to the student on purpose, because a silent downgrade reads as a bug.
     */
    private String assessedLevel;

    /** 0..1 — how sure the model is, and what gates any roadmap fast-track. */
    private Double confidence;

    /** One sentence saying why the level landed where it did. */
    private String justification;
}
