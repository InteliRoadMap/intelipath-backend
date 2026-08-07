package com.inteliroadmap.backend.domain.dto.response.portfolio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single repository in the "Sync GitHub" picker: the raw repo facts plus a computed
 * quality score (0-100) and a coarse tier used to badge/highlight the strongest projects.
 * The AI portfolio summary is NOT computed here — it runs only when the student actually
 * selects a repo to import (keeps the listing fast and cheap).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GithubRepoRankResponse {
    private String name;
    private String fullName;
    private String repoUrl;
    private String description;
    private String homepage;
    private String language;
    private int stars;
    private int forks;
    private boolean isPrivate;
    private boolean fork;

    /** ISO-8601 timestamp of the last push, or null if unknown. */
    private String lastPushedAt;

    /** Computed quality score, 0-100 (higher = stronger portfolio candidate). */
    private int qualityScore;

    /** Coarse bucket derived from qualityScore: HIGH, MEDIUM, or LOW. */
    private String qualityTier;

    /** Short human-readable reasons that drove the score, for UI hints (e.g. "12★", "recent"). */
    private java.util.List<String> highlights;

    /**
     * Every signal that fed {@code qualityScore}, including the ones worth zero.
     *
     * <p>The score was previously a bare number next to a repository, which invites the
     * reading that an AI judged the project. Nothing here is AI: this is arithmetic over
     * GitHub metadata, and it is shown in full so a student can disagree with it. The
     * zero-point lines are the useful ones — "no description, 0 of 10" is the only part
     * of this the student can actually act on.
     */
    private java.util.List<ScoreLine> scoreBreakdown;

}
