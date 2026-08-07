package com.inteliroadmap.backend.domain.dto.response.student;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The student's current level, shared by every feature that adapts to it — the
 * roadmap, Market Pulse and the AI mentor.
 *
 * <p>There is deliberately no "unknown" value. A student who skipped the
 * assessment has no level at all, and the absence is expressed by returning
 * nothing rather than by a placeholder — "we have not asked" and "we asked and
 * the answer was FRESHER" must not render the same way.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentLevelResponse {

    /** BEGINNER | FRESHER | JUNIOR | MID | SENIOR | EXPERT. */
    private String level;

    /** Why it landed there, in the words the student was already shown. */
    private String rationale;

    /** ASSESSMENT | MENTOR_OVERRIDE — where the level came from. */
    private String source;

    /** Share of the career's required skills at APPLIED or above. */
    private Double coverage;

    /**
     * Share backed by an objective source (transcript, repository, mentor).
     * Below 0.30 the level is capped at JUNIOR, so this is the number that
     * explains why a confident self-report did not produce a higher level.
     */
    private Double verifiedCoverage;

    /**
     * The verified share below which the level cannot exceed JUNIOR.
     *
     * <p>On the wire so the UI can explain the ceiling with the number that
     * actually enforces it, instead of hardcoding 0.30 in a second place that
     * then drifts from {@code SeniorityCalculator.VERIFIED_FLOOR}.
     */
    private Double verifiedFloor;

    /** Skills the career requires — the denominator behind both coverage figures. */
    private Integer requiredCount;

    /** Of those, how many the student holds at APPLIED or above. */
    private Integer heldCount;

    /**
     * Of those held, how many have an objective source behind them.
     *
     * <p>Sent as a count rather than left to be re-derived from
     * {@link #verifiedCoverage}, so a prompt can say "2 of 29" and be exactly
     * right rather than a rounding away from it.
     */
    private Integer verifiedCount;

    /** ISO-8601 instant of the run behind this level. */
    private String assessedAt;
}
