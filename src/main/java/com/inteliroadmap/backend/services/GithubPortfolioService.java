package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.GithubImportRequest;
import com.inteliroadmap.backend.domain.dto.response.portfolio.GithubRepoRankResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.PortfolioResponse;

import java.util.List;

public interface GithubPortfolioService {

    PortfolioResponse.PortfolioProjectResponse importFromGithub(GithubImportRequest request);

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
    List<PortfolioResponse.PortfolioProjectResponse> importBatch(List<String> repoUrls);
}
