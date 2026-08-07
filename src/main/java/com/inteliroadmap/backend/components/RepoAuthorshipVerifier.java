package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.clients.GithubApiClient.ContributorStat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Did this student actually write anything in this repository?
 *
 * <p>Nothing used to ask. A repository was read, its README summarised, and skills were
 * credited to whoever pressed Import — the closest thing to a check was
 * {@code isWorkedInFork()}, which tests whether a fork was pushed to after being forked
 * and therefore passes on a one-line README edit. It answers whether the repository was
 * touched, never by whom.
 *
 * <p>The verdict deliberately has three values, not two. A student who contributed
 * nothing and a repository we could not get an answer about are different situations,
 * and only the first is theirs. Collapsing them would mean a GitHub outage, a rate limit,
 * or a repository too large for the statistics endpoint silently reads as "this person is
 * claiming work they did not do" — the system would accuse a student of padding their
 * portfolio because a request timed out.
 *
 * <p>So {@link Verdict#NOT_CONTRIBUTED} is returned only on a positive finding: GitHub
 * answered, the answer was a real contributor list, and this student is not in it.
 */
@Component
@Slf4j
public class RepoAuthorshipVerifier {

    public enum Verdict {
        /** GitHub lists this student among the contributors. */
        CONTRIBUTED,
        /** GitHub answered with a real contributor list and this student is not in it. */
        NOT_CONTRIBUTED,
        /** No usable answer. Never treated as evidence of anything. */
        UNKNOWN
    }

    /**
     * @param verdict      what was established
     * @param authorLogin  the login checked, or null when the student has none linked
     * @param authorCommits commits by this student, 0 when none or unknown
     * @param totalCommits  commits by everyone in the returned list, 0 when unknown
     * @param reason       plain-language explanation, shown to the student on the audit screen
     */
    public record Authorship(Verdict verdict, String authorLogin, int authorCommits,
                             int totalCommits, String reason) {

        /** Share of the repository's commits written by this student, 0..1. */
        public double share() {
            return totalCommits > 0 ? (double) authorCommits / totalCommits : 0.0;
        }
    }

    /**
     * @param contributors GitHub's contributor list; {@code null} means the call failed or the
     *                     statistics were not ready, which is not the same as an empty list
     * @param authorLogin  the student's linked GitHub login
     */
    public Authorship verify(List<ContributorStat> contributors, String authorLogin) {
        if (authorLogin == null || authorLogin.isBlank()) {
            return new Authorship(Verdict.UNKNOWN, null, 0, 0,
                    "No GitHub account is linked, so commits could not be attributed to you.");
        }
        if (contributors == null) {
            return new Authorship(Verdict.UNKNOWN, authorLogin, 0, 0,
                    "GitHub did not return a contributor list for this repository, so authorship "
                            + "could not be checked.");
        }
        if (contributors.isEmpty()) {
            // An empty repository, or one whose whole history GitHub attributes to nobody.
            // Real, but not a finding against the student.
            return new Authorship(Verdict.UNKNOWN, authorLogin, 0, 0,
                    "GitHub reports no contributors at all for this repository.");
        }

        int total = contributors.stream().mapToInt(ContributorStat::commits).sum();
        int mine = contributors.stream()
                .filter(contributor -> contributor.login().equalsIgnoreCase(authorLogin))
                .mapToInt(ContributorStat::commits)
                .sum();

        if (mine > 0) {
            return new Authorship(Verdict.CONTRIBUTED, authorLogin, mine, total,
                    String.format(Locale.ROOT, "You wrote %d of %d commits (%.0f%%).",
                            mine, total, 100.0 * mine / Math.max(total, 1)));
        }

        // Present in the list with zero commits is the same standing as absent: GitHub
        // answered, and its answer credits this student with nothing.
        return new Authorship(Verdict.NOT_CONTRIBUTED, authorLogin, 0, total,
                String.format(Locale.ROOT,
                        "GitHub credits none of this repository's %d commits to @%s.", total, authorLogin));
    }
}
