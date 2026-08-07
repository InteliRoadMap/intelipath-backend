package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.response.portfolio.PortfolioProjectResponse;

import com.inteliroadmap.backend.domain.dto.request.GithubImportRequest;
import com.inteliroadmap.backend.domain.dto.response.portfolio.GithubImportAuditResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.GithubRepoRankResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.PortfolioResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.RepoEvidenceResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.RepoSourcePlanResponse;

import java.util.List;
import java.util.UUID;

public interface GithubPortfolioService {

    PortfolioProjectResponse importFromGithub(GithubImportRequest request);

    /**
     * Lists the authenticated student's own GitHub repositories (public and private), ranked
     * by a quality heuristic, using their stored OAuth token. No AI analysis is run here.
     */
    List<GithubRepoRankResponse> listRankedRepos();

    /**
     * Runs the AI portfolio analysis over each selected repo URL and returns the resulting
     * (unsaved) project entries, mirroring {@link #importFromGithub}. Repos that fail to
     * analyze are skipped rather than failing the whole batch.
     */
    List<PortfolioProjectResponse> importBatch(List<String> repoUrls);

    /** Resolves the exact production-source sample that the next import will read. */
    List<RepoSourcePlanResponse> planBatchAnalysis(List<String> repoUrls);

    /**
     * The record of how one repository was analysed: the files read and their sizes, the
     * catalog and model used, and each matched skill with what the profile has since done
     * with it.
     *
     * @return null when this student has no audit for that URL — an import from before
     *         auditing existed, or a repository they never imported. Absence is a real
     *         answer and is reported as one rather than as an empty audit.
     */
    GithubImportAuditResponse getImportAudit(String repoUrl);

    /**
     * Reads a stored import audit for a particular student.  This method performs no
     * caller authorization itself: the mentor-facing service must first establish
     * that the caller owns a portfolio-review relationship with {@code studentId}.
     * It returns the saved analysis only; it never contacts GitHub or exposes a token.
     */
    GithubImportAuditResponse getImportAuditForStudent(UUID studentId, String repoUrl);

    /** Sanitized evidence for a repository actually displayed on a public portfolio. */
    GithubImportAuditResponse getPublicImportAudit(String portfolioSlug, String repoUrl);

    /**
     * The skills this repository is currently vouching for on the student's profile.
     *
     * <p>Read-only, and safe to call for a repository that was never imported — the
     * answer is then an empty list, which is the correct answer to "what would I lose".
     */
    RepoEvidenceResponse getRepoEvidence(String repoUrl);

    /**
     * Retracts everything this repository proved, at the student's request.
     *
     * <p>Called when they delete the portfolio project <em>and</em> say they want the
     * skills to go with it. Deleting the project alone never reaches here: a showcase
     * is a place to display work, and taking something off display is not a statement
     * that the work was never done.
     *
     * @return what was withdrawn, so the caller can tell the student what changed
     */
    RepoEvidenceResponse withdrawRepoEvidence(String repoUrl);
}
