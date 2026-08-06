package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.response.market.SkillDemandResponse;

import java.util.Map;
import java.util.UUID;

/**
 * How much the job market is currently asking for each catalog skill.
 *
 * <p>This is a read-only view derived from data the scraper already collects
 * ({@code recruitments} plus the {@code skill_trends} rows that
 * {@code SkillExtractionServiceImpl} builds from them). It exists so the roadmap
 * can answer "why am I being told to learn this?" with a number instead of an
 * assertion.
 *
 * <p>Deliberately additive: nothing that already works reads this service, and
 * every field it produces is optional on the wire. A caller that cannot get a
 * demand figure should render the roadmap exactly as it did before.
 *
 * <p><b>Not</b> the full {@code career_targets} model from the design doc. That
 * one slices demand by (career, seniority, location) and needs a minimum sample
 * of 30 postings per slice; with roughly 200 postings in the window most slices
 * would fall under it. This service reports demand per skill across the whole
 * window and is honest about the sample size, which is the most the current data
 * supports.
 */
public interface MarketDemandService {

    /** Rolling window, in days, that {@link #demandBySkill()} aggregates over. */
    int WINDOW_DAYS = 90;

    /**
     * Demand for the skills that matter to one career, keyed by skill id.
     *
     * <p>Takes a career because the answer depends on one. The parameterless
     * version returned a single global ranking that every student saw — AI, Agile,
     * Python at the top for a Frontend student as much as a Data Engineer — since
     * it measured only how common a skill is. See {@code MarketDemandMapper} for
     * the weighting that replaced it and the evidence behind it.
     *
     * <p>Skills the market never mentioned are absent rather than present with a
     * zero, so callers can tell "no demand recorded" apart from "demand is zero",
     * which are different claims. Skills the career's catalog does not name are
     * absent too: with no grading and no document frequency there is nothing to
     * weigh them by.
     *
     * @param careerId the career being viewed; null yields an empty map rather
     *                 than a global ranking, because a global ranking is the thing
     *                 this replaced
     */
    Map<UUID, SkillDemandResponse> demandBySkill(UUID careerId);

    /**
     * Raw posting counts per skill — how many postings named it, and nothing else.
     *
     * <p>Exists because {@link #demandBySkill(UUID)} answers a different question
     * than a student reading a screen does. Relevance measures how
     * <em>characteristic</em> a skill is of one career, so Go — named by 39
     * postings but also by four of the eight careers — used to fall under the
     * relevance gate and vanish from Backend's map. The chooser then printed
     * "No posting data" beside Go, which is false: the data exists and says 39.
     *
     * <p>That gate now runs on weighted demand instead
     * ({@code MarketDemandMapper.MIN_WEIGHTED_DEMAND}), which fixes the same class of
     * disappearance at its source. This method still earns its place: it is free of
     * any career's catalog and grading, so it reports skills the catalog never listed.
     *
     * <p>So relevance keeps its job (ranking, gating what the roadmap urges) and
     * this one takes over display. No career parameter: "how many postings named
     * this skill" does not depend on which career is looking, and filtering by
     * the career's catalog is what silenced Node.js and Kotlin, which the catalog
     * simply never listed.
     *
     * <p>{@code relevance} is null on every entry here — absent rather than zero,
     * because these figures were never weighed against a career.
     */
    Map<UUID, SkillDemandResponse> rawDemandBySkill();
}
