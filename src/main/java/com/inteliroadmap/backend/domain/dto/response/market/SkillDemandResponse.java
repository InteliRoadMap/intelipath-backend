package com.inteliroadmap.backend.domain.dto.response.market;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * How often one skill appears in recent job postings.
 *
 * <p>Carries the raw counts alongside the ratio on purpose: "61%" means something
 * very different backed by 120 postings than by 3, and the UI is expected to show
 * that difference rather than hide it behind a percentage.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillDemandResponse {

    /** Postings mentioning this skill, divided by {@link #sampleSize}. Range 0..1. */
    private Double frequency;

    /** Number of postings in the window that mentioned this skill. */
    private Integer jobCount;

    /** Total postings the ratio was computed against. */
    private Integer sampleSize;

    /**
     * REQUIRED / PREFERRED / OPTIONAL, by the frequency bands the plan fixes for
     * {@code career_targets} (§3.2): >=0.50, >=0.20, >=0.08. Below 0.08 the skill
     * is dropped rather than labelled, so this is never null on a returned row.
     *
     * <p>Deliberately the same vocabulary as {@code career_targets.importance}
     * even though this figure is not yet grouped by career/seniority/location —
     * when Phase 3 lands and replaces the source of these numbers, the wire
     * contract and every UI reading it stay put.
     */
    private String importance;

    /**
     * One sentence a student can act on, e.g. "61% of recent postings ask for
     * this". The plan (§6.5) treats this as the visible payoff of the whole
     * market layer, so it is built server-side once rather than re-phrased by
     * each client.
     */
    private String reason;

    /** Days the window spans, so the UI can caption the figure honestly. */
    private Integer windowDays;

    /**
     * How much this skill distinguishes <em>this</em> career, not just how common
     * it is: {@code frequency × importanceWeight × ln(careers / (careersNaming + 1))}.
     *
     * <p>This is what the roadmap ranks and gates on. {@link #frequency} stayed as
     * the number a student can read ("19% of postings ask for this"), but it is no
     * longer what decides whether a skill is worth showing — ranking on it gave
     * every one of the eight careers the same list, led by AI, Agile and Python,
     * because those are the skills that discriminate least. The weighting is the
     * TF-IDF transposition from de Groot et al., "Job Posting-Enriched Knowledge
     * Graph for Skills-based Matching" (RecSys in HR 2021), which states the two
     * criteria a skill must meet: frequently required <em>and</em> characteristic.
     *
     * <p>Career-specific, so the same skill carries a different value on a Backend
     * roadmap than on a Frontend one.
     */
    private Double relevance;

    /**
     * Careers whose catalog names this skill. 1 means it is a signature of one
     * role; 8 means it says nothing about which role you are heading for.
     *
     * <p>Sent so the UI can explain a ranking rather than assert it.
     */
    private Integer careersNaming;
}
