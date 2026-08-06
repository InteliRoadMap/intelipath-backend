package com.inteliroadmap.backend.domain.dto.response.roadmap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentRoadmapResponse {
    private String targetCareerRole;
    private Integer progress;
    private List<RoadmapNodeResponse> nodes;

    /**
     * The connections between those nodes, computed for this student.
     *
     * <p>Null on endpoints that serve a career template rather than a person —
     * there is no profile to order by, so the client keeps deriving edges from
     * the node fields as it always did.
     */
    private List<RoadmapEdgeResponse> edges;

    /**
     * Standalone roadmaps under this career (languages, frameworks, database
     * tracks) that the student can enter, rather than nodes lying on the path.
     *
     * <p>Null or empty for a career that keeps everything on one path, and for
     * a career that is nothing but imported roadmaps — there, they stay inline
     * because moving them all behind a click would leave an empty page.
     */
    private List<SubRoadmapResponse> subRoadmaps;

    /**
     * Where this view sits, outermost first: {@code Backend › Java › Spring Boot}.
     *
     * <p>Null on the career's own roadmap, which is the root and has nowhere to
     * go back to. Present once the student has entered a sub-roadmap — drilling
     * in without a way to see how deep you are is how people get lost.
     */
    private List<RoadmapCrumbResponse> breadcrumb;

    /**
     * The node this view is rooted at, or null on the career's own roadmap.
     *
     * <p>Not one of {@link #nodes} — see {@link RoadmapRootResponse} for why it
     * is withheld from the drawing and why the client still needs it.
     */
    private RoadmapRootResponse rootNode;

    // ── Career readiness (additive; nullable) ────────────────────────────────
    // Not the same measure as `progress`, and the difference is the point:
    // `progress` counts nodes ticked off on this view, readiness counts the
    // career's essential skills actually held. A student can finish every node
    // in front of them and still be 12% ready for the job.
    /**
     * Share of the career's essential skills the student holds, 0..1, counting
     * both self-declared and verified. {@code SeniorityCalculator.ratioAll}.
     */
    private Double readiness;

    /**
     * The part of {@link #readiness} backed by objective evidence — a GitHub
     * repository read, a transcript — rather than the student's own account of
     * themselves. {@code SeniorityCalculator.ratioVerified}.
     */
    private Double readinessVerified;

    /** How many essential skills the ratios are over, so the % can be checked. */
    private Integer readinessRequiredCount;
    private Integer readinessHeldCount;
    private Integer readinessVerifiedCount;

    /**
     * The skills those counts are over, one row each, held or missing.
     *
     * <p>Carries the readiness denominator itself rather than only its size, so
     * the skill map draws the same set the badge counts. Null on the career
     * template endpoints, where there is no student to measure.
     */
    private List<CoreSkillResponse> coreSkills;
}
