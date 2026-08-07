package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.clients.GithubApiClient.GithubRepoSummary;
import com.inteliroadmap.backend.domain.dto.response.portfolio.GithubRepoRankResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.ScoreLine;
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
        // Recorded alongside the running total so the two can never disagree: every line
        // added here is the same number added to `total` on the line above it.
        List<ScoreLine> breakdown = new ArrayList<>();
        int total = 0;

        // Stars — log-scaled so a handful of stars still counts but a viral repo doesn't dominate.
        int starScore = (int) Math.min(SCORE_STARS_MAX, Math.round(log2(repo.stars() + 1) * 8.0));
        total += starScore;
        breakdown.add(new ScoreLine("Stars", starScore, SCORE_STARS_MAX,
                repo.stars() + " star(s), log-scaled so one popular repo cannot dominate"));
        if (repo.stars() > 0) {
            highlights.add(repo.stars() + "★");
        }

        // Recency of the last push.
        int recencyScore = recencyScore(repo.pushedAt());
        total += recencyScore;
        breakdown.add(new ScoreLine("Recent activity", recencyScore, SCORE_RECENCY_MAX,
                describeRecency(repo.pushedAt())));
        if (recencyScore >= 20) {
            highlights.add("recently active");
        }

        // A real description signals a presentable, documented project.
        boolean described = repo.description() != null && !repo.description().isBlank();
        if (described) {
            total += SCORE_DESCRIPTION;
        } else {
            highlights.add("no description");
        }
        breakdown.add(new ScoreLine("Description",
                described ? SCORE_DESCRIPTION : 0, SCORE_DESCRIPTION,
                described ? "has a description on GitHub" : "no description on GitHub — add one to gain these points"));

        // Work the student did themselves is what a portfolio should showcase — which
        // is not the same question as whether GitHub calls the repository a fork.
        //
        // Scoring on the flag alone ranked two bookmark forks of other people's
        // repositories (30 points each) above the student's own main project (25),
        // which was a fork of their team's organisation repository and pushed to
        // daily. Forking the team repo and working in the fork is the ordinary shape
        // of a university project, and the ranking read it as copied code.
        if (!repo.fork()) {
            total += SCORE_ORIGINAL;
            highlights.add("original");
            breakdown.add(new ScoreLine("Your own work", SCORE_ORIGINAL, SCORE_ORIGINAL,
                    "not a fork"));
        } else if (repo.isWorkedInFork()) {
            total += SCORE_ORIGINAL;
            highlights.add("your work in a fork");
            breakdown.add(new ScoreLine("Your own work", SCORE_ORIGINAL, SCORE_ORIGINAL,
                    "a fork, but pushed to after you forked it — that is your work"));
        } else {
            highlights.add("fork");
            breakdown.add(new ScoreLine("Your own work", 0, SCORE_ORIGINAL,
                    "a fork with no pushes since you forked it, so it reads as a bookmark"));
        }

        // Language relevant to the student's target career.
        boolean languageMatches = matchesCareer(repo.language(), catalog);
        if (languageMatches) {
            total += SCORE_LANGUAGE_MATCH;
            highlights.add("matches " + repo.language());
        }
        breakdown.add(new ScoreLine("Career language",
                languageMatches ? SCORE_LANGUAGE_MATCH : 0, SCORE_LANGUAGE_MATCH,
                describeLanguageMatch(repo.language(), catalog, languageMatches)));

        // A few forks by others is a mild quality signal.
        int forkScore = Math.min(SCORE_FORKS_MAX, repo.forks() * 2);
        total += forkScore;
        breakdown.add(new ScoreLine("Forked by others", forkScore, SCORE_FORKS_MAX,
                repo.forks() + " fork(s) of this repository"));

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
                .scoreBreakdown(breakdown)
                .build();
    }

    private String describeRecency(OffsetDateTime pushedAt) {
        if (pushedAt == null) {
            return "GitHub reports no last-push date";
        }
        long days = ChronoUnit.DAYS.between(pushedAt, OffsetDateTime.now());
        return "last push " + days + " day(s) ago";
    }

    /**
     * Says which career skill the language matched, not merely that one did.
     *
     * <p>The match is a loose substring test, so it can fire for reasons that look wrong
     * from outside — naming the skill it hit is what makes the result arguable instead of
     * mysterious.
     */
    private String describeLanguageMatch(String language, List<String> catalog, boolean matched) {
        if (language == null || language.isBlank()) {
            return "GitHub reports no primary language for this repository";
        }
        if (!matched) {
            return language + " is not in your career's skill list";
        }
        String lang = language.toLowerCase(Locale.ROOT);
        String hit = catalog.stream()
                .filter(skill -> skill.contains(lang) || lang.contains(skill))
                .findFirst()
                .orElse(lang);
        return language + " matches the career skill \"" + hit + "\"";
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
