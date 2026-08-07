package com.inteliroadmap.backend.mappers;

import com.inteliroadmap.backend.domain.dto.response.market.SkillDemandResponse;
import com.inteliroadmap.backend.domain.enums.ImportanceLevel;
import org.springframework.stereotype.Component;

/**
 * Builds {@link SkillDemandResponse} from the raw counts behind a skill's demand.
 *
 * <p>Keeping the banding in a mapper rather than the service means the roadmap,
 * the market page and the gap report all describe the same evidence with the same
 * words.
 *
 * <h2>Why relevance replaced a raw frequency threshold</h2>
 *
 * <p>Demand used to be a single global figure gated at 8% of postings. Measured
 * on 866 real postings that produced one list for all eight careers — AI 27%,
 * Agile 24%, Python 21%, SQL 19% — because it ranked on how <em>common</em> a
 * skill is, and the most common skills are exactly the ones that do not tell a
 * Backend student from a Frontend one. A Frontend student was told to learn AI
 * before TypeScript.
 *
 * <p>de Groot et al., "Job Posting-Enriched Knowledge Graph for Skills-based
 * Matching" (RecSys in HR 2021, arXiv:2109.02554) name the two criteria a skill
 * has to meet: it must be <em>frequently required</em> within its context and
 * <em>characteristic</em> for that context. They transpose TF-IDF from terms in
 * documents onto skills in occupation groups. Same move here, with the eight
 * careers as the documents.
 *
 * <p>The same 866 postings under the new weighting put TypeScript, React, HTML5
 * and CSS3 at the top for Frontend, and SQL, Microservices, Docker and Java for
 * Backend, with AI and Agile gone from both.
 *
 * <h2>CORRECTION — relevance ranks, it does not decide who gets mentioned</h2>
 *
 * <p>The section above is right about ranking and was wrong about gating, and the two
 * were conflated here for months. Relevance was also used as the threshold for whether
 * a skill got a response object at all, which turned "AI and Agile gone from both" from
 * a ranking result into a deletion. With eight careers the IDF term hits zero at seven
 * and goes negative at eight, so on 913 postings Python (193 mentions) scored 0.0000
 * and Java (158) scored −0.0204 — and both disappeared from the Skill Map, from Market
 * Pulse, and from a student's own verified-skill display. A student holding Java at
 * PROFESSIONAL, verified by GitHub, could not find it on a chart of their own skills.
 *
 * <p>The mistake was treating one number as the answer to two questions. "Which skill
 * distinguishes this career" and "how much does the market want this" are different
 * questions with different right answers, and a generic skill scores low on the first
 * and high on the second — correctly, both times. So:
 *
 * <ul>
 *   <li>{@code relevance} keeps the IDF term and keeps ordering the roadmap, clamped at
 *       zero so it can never argue a student away from a skill for being popular;
 *   <li>{@code frequency} — the plain share of postings — is what the Skill Map's
 *       horizontal axis reads, because that axis is labelled "the market wants this
 *       more" and that is the question it asks;
 *   <li>{@link #MIN_WEIGHTED_DEMAND} decides who is mentioned at all, on demand rather
 *       than on distinctiveness.
 * </ul>
 */
@Component
public class MarketDemandMapper {

    /**
     * Fewest postings in the window before any ratio is computed.
     *
     * <p>Below this the denominator is too small for a percentage to mean
     * anything: three postings would let one mention read as 33% demand.
     */
    public static final int MIN_SAMPLE = 30;

    /** Frequency bands for the human-readable REQUIRED / PREFERRED / OPTIONAL label. */
    public static final double REQUIRED_AT = 0.50;
    public static final double PREFERRED_AT = 0.20;

    /**
     * Weighted demand below which a skill is not reported for a career.
     *
     * <p><b>Gates on demand, not on relevance.</b> It used to gate on relevance, and
     * that was a real defect rather than a tuning problem: relevance carries an IDF
     * term, so a skill every career wants scores zero or less by construction, and a
     * <em>threshold</em> on it deletes the most-wanted skills from the payload
     * entirely. Measured on 913 postings, Python (193 postings, the second-most-asked
     * skill for Backend) scored exactly 0.0000 and Java (158) scored −0.0204; both
     * vanished from the Skill Map, from Market Pulse and from every consumer, because
     * they are wanted by everyone. Ranking a generic skill last is a defensible
     * opinion. Refusing to mention it is not.
     *
     * <p>So relevance still ranks — see {@link #relevanceOf} — but what a career is
     * willing to <em>talk about</em> is now decided by how much of the market asks for
     * it, weighted by how that career grades it. At full weight this floor is about 7
     * postings in 913; an AVG skill needs ~11 and a LOW skill ~23 to clear the same
     * bar, which is the weighting doing its job — a career that calls a skill marginal
     * should need more market evidence before the skill is surfaced anyway.
     *
     * <p>Derived from the distribution, not chosen in advance. Across the 413
     * (skill, career) pairs carrying market data the median weighted demand is 0.0092;
     * this floor keeps 213 pairs, about 27 per career. It sits just under 0.0079 — the
     * weakest pair the old relevance gate happened to surface — so the change is purely
     * additive: no career loses a skill it reports today (108 before, 213 after), and 105
     * pairs are gained.
     *
     * <p>Re-derive whenever the catalog or the posting volume changes materially; it is
     * a property of the data, not a constant of the domain.
     */
    public static final double MIN_WEIGHTED_DEMAND = 0.0075;

    /** How much a career's own grading of a skill counts towards its relevance. */
    public static final double WEIGHT_HIGH = 1.0;
    public static final double WEIGHT_AVG = 0.6;
    public static final double WEIGHT_LOW = 0.3;

    /**
     * @param jobCount      postings in the window naming this skill
     * @param sampleSize    postings in the window overall
     * @param windowDays    span the two counts were taken over
     * @param importance    how the career being viewed grades this skill
     * @param careersNaming how many of the careers name it at all
     * @param careerCount   how many careers exist — the corpus size
     * @return the response, or null when the sample is too small or the skill too
     *         marginal for this career to report — null means "say nothing", not
     *         "say zero"
     */
    public SkillDemandResponse toSkillDemandResponse(int jobCount, long sampleSize, int windowDays,
                                                     ImportanceLevel importance,
                                                     int careersNaming, int careerCount) {
        if (jobCount <= 0 || sampleSize < MIN_SAMPLE || careerCount <= 0) {
            return null;
        }

        // A skill can out-count the window only if skill_trends is stale relative
        // to recruitments; clamp so no caller has to render a bar past full.
        double frequency = Math.min(1.0, (double) jobCount / sampleSize);
        if (frequency * weightOf(importance) < MIN_WEIGHTED_DEMAND) {
            return null;
        }
        double relevance = relevanceOf(frequency, importance, careersNaming, careerCount);

        return SkillDemandResponse.builder()
                .frequency(frequency)
                .jobCount(jobCount)
                .sampleSize((int) sampleSize)
                .relevance(relevance)
                .careersNaming(careersNaming)
                .importance(importanceOf(frequency))
                .reason(reasonFor(frequency, sampleSize, windowDays, careersNaming, careerCount))
                .windowDays(windowDays)
                .build();
    }

    /**
     * {@code max(0, frequency × weight(importance) × ln(careerCount / (careersNaming + 1)))}.
     *
     * <p>Answers "how much does this skill <em>distinguish</em> this career", which is
     * what orders the roadmap. It is not a demand figure and must not be read as one —
     * for that, {@code frequency} is the plain share of postings, and the Skill Map's
     * horizontal axis uses it precisely because that axis is labelled "the market wants
     * this more".
     *
     * <p><b>Clamped at zero.</b> With eight careers the smoothed {@code +1} makes
     * {@code ln(8/9)} negative for a skill every career names, and the old code treated
     * that as a feature — "pushed below every specific one rather than merely tied". It
     * is not: {@code RoadmapEdgeResolver} divides this by the career's maximum to get a
     * market pull, and a negative pull means the roadmap actively pushes a student
     * <em>away</em> from Java for being too widely wanted. Zero says "this tells us
     * nothing about which career you are in", which is the true statement. Ordering
     * among everything above zero is unchanged.
     *
     * <p>A skill no career names ({@code careersNaming == 0}) cannot be scored for
     * a career at all — this method is only ever reached through a career's own
     * catalog row, so the smallest real value is 1.
     */
    public double relevanceOf(double frequency, ImportanceLevel importance,
                              int careersNaming, int careerCount) {
        return Math.max(0.0, frequency * weightOf(importance)
                * Math.log((double) careerCount / (careersNaming + 1)));
    }

    public double weightOf(ImportanceLevel importance) {
        if (importance == ImportanceLevel.HIGH) return WEIGHT_HIGH;
        if (importance == ImportanceLevel.AVG) return WEIGHT_AVG;
        return WEIGHT_LOW;
    }

    /** REQUIRED / PREFERRED / OPTIONAL, by how much of the market asks for it. */
    public String importanceOf(double frequency) {
        if (frequency >= REQUIRED_AT) return "REQUIRED";
        if (frequency >= PREFERRED_AT) return "PREFERRED";
        return "OPTIONAL";
    }

    /**
     * The sentence shown on the node. It states the sample the percentage rests
     * on, and — when the skill is a signature of few roles — says so, because that
     * is the half of the ranking a raw percentage cannot explain.
     */
    public String reasonFor(double frequency, long sampleSize, int windowDays,
                            int careersNaming, int careerCount) {
        String base = String.format("%d%% of %d postings in the last %d days ask for this",
                Math.round(frequency * 100), sampleSize, windowDays);
        if (careersNaming <= 0 || careersNaming >= careerCount) {
            return base;
        }
        if (careersNaming == 1) {
            return base + ", and no other career here asks for it";
        }
        return base + String.format(", and %d of %d careers here ask for it", careersNaming, careerCount);
    }
}
