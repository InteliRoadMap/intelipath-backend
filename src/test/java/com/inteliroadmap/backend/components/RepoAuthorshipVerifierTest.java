package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.clients.GithubApiClient.ContributorStat;
import com.inteliroadmap.backend.components.RepoAuthorshipVerifier.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The verdict gates whether a repository is allowed to change a student's profile, so the
 * tests that matter most are the ones proving it stays silent when it does not know.
 */
class RepoAuthorshipVerifierTest {

    private final RepoAuthorshipVerifier verifier = new RepoAuthorshipVerifier();

    @Test
    @DisplayName("a contributor is credited with their share of the commits")
    void contributorIsCredited() {
        var result = verifier.verify(
                List.of(new ContributorStat("dpvinh30092005", 143), new ContributorStat("someone", 369)),
                "dpvinh30092005");

        assertThat(result.verdict()).isEqualTo(Verdict.CONTRIBUTED);
        assertThat(result.authorCommits()).isEqualTo(143);
        assertThat(result.totalCommits()).isEqualTo(512);
        assertThat(result.share()).isCloseTo(0.279, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("login matching ignores case — GitHub does")
    void loginMatchIsCaseInsensitive() {
        var result = verifier.verify(List.of(new ContributorStat("DpVinh30092005", 5)), "dpvinh30092005");
        assertThat(result.verdict()).isEqualTo(Verdict.CONTRIBUTED);
    }

    @Test
    @DisplayName("a real list without this student is a positive finding of no contribution")
    void absentFromRealListIsNotContributed() {
        var result = verifier.verify(
                List.of(new ContributorStat("torvalds", 900), new ContributorStat("someone", 100)),
                "dpvinh30092005");

        assertThat(result.verdict()).isEqualTo(Verdict.NOT_CONTRIBUTED);
        assertThat(result.totalCommits()).isEqualTo(1000);
        assertThat(result.reason()).contains("@dpvinh30092005");
    }

    @Test
    @DisplayName("a failed lookup is UNKNOWN, never NOT_CONTRIBUTED")
    void failedLookupIsUnknown() {
        // The distinction this whole class exists for: a timeout must not read as a student
        // claiming work they did not do.
        var result = verifier.verify(null, "dpvinh30092005");

        assertThat(result.verdict()).isEqualTo(Verdict.UNKNOWN);
        assertThat(result.reason()).contains("did not return");
    }

    @Test
    @DisplayName("an empty contributor list is UNKNOWN, not an accusation")
    void emptyListIsUnknown() {
        assertThat(verifier.verify(List.of(), "dpvinh30092005").verdict()).isEqualTo(Verdict.UNKNOWN);
    }

    @Test
    @DisplayName("no linked account means nothing can be attributed either way")
    void noLinkedLoginIsUnknown() {
        assertThat(verifier.verify(List.of(new ContributorStat("someone", 5)), null).verdict())
                .isEqualTo(Verdict.UNKNOWN);
        assertThat(verifier.verify(List.of(new ContributorStat("someone", 5)), "  ").verdict())
                .isEqualTo(Verdict.UNKNOWN);
    }

    @Test
    @DisplayName("listed with zero commits stands the same as not listed")
    void listedWithZeroCommitsIsNotContributed() {
        var result = verifier.verify(
                List.of(new ContributorStat("dpvinh30092005", 0), new ContributorStat("other", 40)),
                "dpvinh30092005");

        assertThat(result.verdict()).isEqualTo(Verdict.NOT_CONTRIBUTED);
    }

    @Test
    @DisplayName("share is zero rather than a division by zero when nothing is known")
    void shareIsSafeWhenTotalIsZero() {
        assertThat(verifier.verify(null, "someone").share()).isZero();
    }
}
