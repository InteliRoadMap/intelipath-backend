package com.inteliroadmap.backend.clients;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Separating a fork the student works in from a fork they bookmarked.
 *
 * <p>GitHub's `fork` flag says who copied the repository, not who wrote the code in
 * it, and the ranking used to strip the originality bonus on that flag alone. The
 * measured result: two bookmark forks of other people's repositories scored 30 each
 * while the student's own main project — forked from their team's organisation and
 * pushed to daily — scored 25 and sank below them.
 */
class GithubRepoSummaryForkTest {

    private static final OffsetDateTime FORKED_AT = OffsetDateTime.parse("2026-07-01T00:00:00Z");

    @Test
    @DisplayName("a fork pushed to after it was created is the student's own work")
    void aForkPushedToAfterForkingCounts() {
        assertThat(fork(FORKED_AT, FORKED_AT.plusDays(27)).isWorkedInFork()).isTrue();
    }

    @Test
    @DisplayName("a bookmark fork inherits the upstream's older pushed_at and does not count")
    void aBookmarkForkDoesNotCount() {
        assertThat(fork(FORKED_AT, FORKED_AT.minusDays(3)).isWorkedInFork()).isFalse();
    }

    @Test
    @DisplayName("a repository that is not a fork is never reported as a worked-in fork")
    void aPlainRepositoryIsNotAFork() {
        GithubApiClient.GithubRepoSummary repo = new GithubApiClient.GithubRepoSummary(
                "tour-vista", "me/tour-vista", "", null, null, "Java",
                0, 0, false, false, false, FORKED_AT.plusDays(1), FORKED_AT);
        assertThat(repo.isWorkedInFork()).isFalse();
    }

    @Test
    @DisplayName("missing timestamps answer no — absent evidence of work is not evidence of work")
    void missingTimestampsAreConservative() {
        assertThat(fork(null, FORKED_AT).isWorkedInFork()).isFalse();
        assertThat(fork(FORKED_AT, null).isWorkedInFork()).isFalse();
    }

    private GithubApiClient.GithubRepoSummary fork(OffsetDateTime createdAt, OffsetDateTime pushedAt) {
        return new GithubApiClient.GithubRepoSummary(
                "intelipath-frontend", "me/intelipath-frontend", "", null, null, "TypeScript",
                0, 0, true, false, false, pushedAt, createdAt);
    }
}
