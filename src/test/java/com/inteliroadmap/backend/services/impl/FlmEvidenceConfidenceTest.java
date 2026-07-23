package com.inteliroadmap.backend.services.impl;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Transcript evidence is only useful if it can actually clear the bar the roadmap checks.
 *
 * <p>The old ladder ran 0.72 / 0.78 / 0.84 / 0.90 in fixed steps and stepped straight over
 * the 0.85 HIGH-importance floor. Every auto-completable Frontend node is HIGH, so two or
 * three subjects covering a skill counted for nothing and a student who declared five terms
 * unlocked no nodes at all. These tests pin the ladder to the thresholds.
 */
class FlmEvidenceConfidenceTest {

    /** RoadmapPersonalizationServiceImpl.HIGH_IMPORTANCE_CONFIDENCE. */
    private static final BigDecimal HIGH_FLOOR = new BigDecimal("0.85");
    /** RoadmapPersonalizationServiceImpl.AVG_IMPORTANCE_CONFIDENCE. */
    private static final BigDecimal AVG_FLOOR = new BigDecimal("0.70");

    @Test
    void oneSubjectIsOrdinaryEvidence() {
        BigDecimal confidence = StudentCurriculumServiceImpl.confidenceFor(1);
        assertEquals(new BigDecimal("0.75"), confidence);
        assertTrue(confidence.compareTo(AVG_FLOOR) >= 0, "one subject should clear an average skill");
        assertTrue(confidence.compareTo(HIGH_FLOOR) < 0, "one subject should not unlock a core skill");
    }

    @Test
    void twoSubjectsClearACoreSkill() {
        BigDecimal confidence = StudentCurriculumServiceImpl.confidenceFor(2);
        assertTrue(confidence.compareTo(HIGH_FLOOR) >= 0,
                "two subjects covering the same skill must clear the HIGH floor — this is the "
                        + "case the old ladder missed by 0.01");
    }

    @Test
    void furtherSubjectsCapOut() {
        assertEquals(new BigDecimal("0.90"), StudentCurriculumServiceImpl.confidenceFor(3));
        assertEquals(new BigDecimal("0.90"), StudentCurriculumServiceImpl.confidenceFor(12));
    }

    @Test
    void neverReturnsMoreThanCertainOrLessThanTheBase() {
        // Coverage is derived from a set, so zero should not happen — but a 0 must not read
        // as stronger evidence than one subject, or index below the ladder.
        assertEquals(new BigDecimal("0.75"), StudentCurriculumServiceImpl.confidenceFor(0));
        assertEquals(new BigDecimal("0.75"), StudentCurriculumServiceImpl.confidenceFor(-3));
    }
}
