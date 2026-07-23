package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.clients.GithubApiClient.GithubRepoSummary;
import com.inteliroadmap.backend.domain.dto.response.portfolio.GithubRepoRankResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Ranks a student's GitHub repositories by a cheap, deterministic quality heuristic so the
 * strongest portfolio candidates float to the top of the picker. No LLM calls here — AI
 * analysis is deferred until the student actually imports a chosen repo.
 *
 * <p>Score is out of 100, summed from independent signals (see the SCORE_* weights). The exact
 * weights are a product judgement call, tuned to favour original, recently-maintained, described
 * work in a language relevant to the student's target career.
 */
@Component
@Slf4j
public class GithubRepoRankingService {

    private static final int SCORE_STARS_MAX = 30;
    private static final int SCORE_RECENCY_MAX = 25;
    private static final int SCORE_DESCRIPTION = 10;
    private static final int SCORE_ORIGINAL = 15;   // not a fork => the student's own work
    private static final int SCORE_LANGUAGE_MATCH = 15;
    private static final int SCORE_FORKS_MAX = 5;

    private static final int TIER_HIGH_MIN = 60;
    private static final int TIER_MEDIUM_MIN = 35;

    /**
     * @param repos        repositories owned by the student
     * @param careerSkills the student's career skill catalog (skill names); used for language relevance
     * @return repos scored and sorted by qualityScore descending
     */
    public List<GithubRepoRankResponse> rank(List<GithubRepoSummary> repos, List<String> careerSkills) {
        List<String> catalog = careerSkills == null ? List.of()
                : careerSkills.stream().filter(s -> s != null && !s.isBlank())
                        .map(s -> s.toLowerCase(Locale.ROOT)).toList();

        List<GithubRepoRankResponse> ranked = new ArrayList<>(repos.size());
        for (GithubRepoSummary repo : repos) {
            // Archived repos are dead weight in a portfolio picker; skip them entirely.
            if (repo.archived()) {
                continue;
            }
            ranked.add(score(repo, catalog));
        }
        ranked.sort(Comparator.comparingInt(GithubRepoRankResponse::getQualityScore).reversed());
        return ranked;
    }

    private GithubRepoRankResponse score(GithubRepoSummary repo, List<String> catalog) {
        List<String> highlights = new ArrayList<>();
        int total = 0;

        // Stars — log-scaled so a handful of stars still counts but a viral repo doesn't dominate.
        int starScore = (int) Math.min(SCORE_STARS_MAX, Math.round(log2(repo.stars() + 1) * 8.0));
        total += starScore;
        if (repo.stars() > 0) {
            highlights.add(repo.stars() + "★");
        }

        // Recency of the last push.
        int recencyScore = recencyScore(repo.pushedAt());
        total += recencyScore;
        if (recencyScore >= 20) {
            highlights.add("recently active");
        }

        // A real description signals a presentable, documented project.
        if (repo.description() != null && !repo.description().isBlank()) {
            total += SCORE_DESCRIPTION;
        } else {
            highlights.add("no description");
        }

        // Original work (not a fork) is what a portfolio should showcase.
        if (!repo.fork()) {
            total += SCORE_ORIGINAL;
            highlights.add("original");
        } else {
            highlights.add("fork");
        }

        // Language relevant to the student's target career.
        if (matchesCareer(repo.language(), catalog)) {
            total += SCORE_LANGUAGE_MATCH;
            highlights.add("matches " + repo.language());
        }

        // A few forks by others is a mild quality signal.
        int forkScore = Math.min(SCORE_FORKS_MAX, repo.forks() * 2);
        total += forkScore;

        total = Math.max(0, Math.min(100, total));

        return GithubRepoRankResponse.builder()
                .name(repo.name())
                .fullName(repo.fullName())
                .repoUrl(repo.htmlUrl())
                .description(repo.description())
                .homepage(repo.homepage())
                .language(repo.language())
                .stars(repo.stars())
                .forks(repo.forks())
                .isPrivate(repo.isPrivate())
                .fork(repo.fork())
                .lastPushedAt(repo.pushedAt() != null ? repo.pushedAt().toString() : null)
                .qualityScore(total)
                .qualityTier(tier(total))
                .highlights(highlights)
                .build();
    }

    private int recencyScore(OffsetDateTime pushedAt) {
        if (pushedAt == null) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(pushedAt, OffsetDateTime.now());
        if (days <= 30) return SCORE_RECENCY_MAX;
        if (days <= 90) return 20;
        if (days <= 180) return 15;
        if (days <= 365) return 10;
        return 5;
    }

    private boolean matchesCareer(String language, List<String> catalog) {
        if (language == null || language.isBlank() || catalog.isEmpty()) {
            return false;
        }
        String lang = language.toLowerCase(Locale.ROOT);
        // Loose match: the language name appears in a catalog skill or vice versa
        // (e.g. language "TypeScript" vs skill "TypeScript", language "Java" vs skill "Java").
        for (String skill : catalog) {
            if (skill.contains(lang) || lang.contains(skill)) {
                return true;
            }
        }
        return false;
    }

    private String tier(int score) {
        if (score >= TIER_HIGH_MIN) return "HIGH";
        if (score >= TIER_MEDIUM_MIN) return "MEDIUM";
        return "LOW";
    }

    private double log2(double value) {
        return Math.log(value) / Math.log(2);
    }
}
