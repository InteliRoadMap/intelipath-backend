package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.clients.GithubApiClient.GithubRepoSummary;
import com.inteliroadmap.backend.domain.dto.response.portfolio.GithubRepoRankResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.ScoreLine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The score breakdown has one job: to be the same arithmetic the score came from.
 *
 * <p>A breakdown assembled separately from the total is worse than no breakdown — it
 * explains a number the student is not looking at. These tests pin the two together.
 */
class GithubRepoRankingServiceTest {

    private final GithubRepoRankingService service = new GithubRepoRankingService();

    private static final List<String> CATALOG = List.of("Java", "Spring Boot", "PostgreSQL");

    @Test
    @DisplayName("the breakdown adds up to the score it explains")
    void breakdownSumsToScore() {
        GithubRepoRankResponse ranked = rankOne(repo("intelipath-backend", "A career roadmap API",
                "Java", 4, 2, false, OffsetDateTime.now().minusDays(3)));

        int summed = ranked.getScoreBreakdown().stream()
                .mapToInt(ScoreLine::points)
                .sum();
        assertThat(summed).isEqualTo(ranked.getQualityScore());
    }

    @Test
    @DisplayName("signals worth zero still appear — those are the ones a student can act on")
    void zeroPointSignalsArelisted() {
        GithubRepoRankResponse ranked = rankOne(repo("scratch", null,
                "Haskell", 0, 0, false, OffsetDateTime.now().minusDays(2)));

        assertThat(ranked.getScoreBreakdown()).extracting(ScoreLine::label)
                .contains("Description", "Career language");
        assertThat(lineFor(ranked, "Description").points()).isZero();
        assertThat(lineFor(ranked, "Description").detail()).contains("no description");
        assertThat(lineFor(ranked, "Career language").points()).isZero();
    }

    @Test
    @DisplayName("a language match names the career skill it hit, so the student can disagree with it")
    void languageMatchNamesTheSkill() {
        GithubRepoRankResponse ranked = rankOne(repo("api", "desc", "Java", 0, 0, false,
                OffsetDateTime.now().minusDays(1)));

        assertThat(lineFor(ranked, "Career language").detail()).contains("Java");
    }

    @Test
    @DisplayName("a bookmark fork is told it scored zero for originality, and why")
    void bookmarkForkExplainsItsZero() {
        OffsetDateTime forkedAt = OffsetDateTime.now().minusDays(10);
        GithubRepoRankResponse ranked = rankOne(new GithubRepoSummary(
                "awesome-list", "me/awesome-list", "https://github.com/me/awesome-list",
                "desc", null, "Java", 0, 0, true, false, false,
                forkedAt.minusDays(3), forkedAt));

        assertThat(lineFor(ranked, "Your own work").points()).isZero();
        assertThat(lineFor(ranked, "Your own work").detail()).contains("bookmark");
    }

    @Test
    @DisplayName("every line's points stay within the maximum it advertises")
    void pointsNeverExceedTheStatedMaximum() {
        GithubRepoRankResponse ranked = rankOne(repo("viral", "desc", "Java", 50_000, 9_000, false,
                OffsetDateTime.now()));

        assertThat(ranked.getScoreBreakdown())
                .allSatisfy(line -> assertThat(line.points()).isBetween(0, line.max()));
    }

    private ScoreLine lineFor(GithubRepoRankResponse ranked, String label) {
        return ranked.getScoreBreakdown().stream()
                .filter(line -> line.label().equals(label))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no breakdown line labelled " + label));
    }

    private GithubRepoRankResponse rankOne(GithubRepoSummary repo) {
        List<GithubRepoRankResponse> ranked = service.rank(List.of(repo), CATALOG);
        assertThat(ranked).hasSize(1);
        return ranked.get(0);
    }

    private GithubRepoSummary repo(String name, String description, String language,
                                   int stars, int forks, boolean fork, OffsetDateTime pushedAt) {
        return new GithubRepoSummary(name, "me/" + name, "https://github.com/me/" + name,
                description, null, language, stars, forks, fork, false, false,
                pushedAt, pushedAt.minusDays(30));
    }
}
