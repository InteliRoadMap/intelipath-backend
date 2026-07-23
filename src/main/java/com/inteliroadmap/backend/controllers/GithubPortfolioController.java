package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.request.GithubImportBatchRequest;
import com.inteliroadmap.backend.domain.dto.request.GithubImportRequest;
import com.inteliroadmap.backend.domain.dto.response.portfolio.GithubRepoRankResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.PortfolioResponse;
import com.inteliroadmap.backend.services.GithubPortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/student/portfolio/projects")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Student E-Portfolio", description = "Endpoints for managing student e-portfolios")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasRole('STUDENT')")
public class GithubPortfolioController {

    private final GithubPortfolioService githubPortfolioService;

    @PostMapping("/github-import")
    @Operation(summary = "Import Project from GitHub", description = "Extracts repo info and README, uses AI to summarize, and returns project info without saving.")
    public ResponseEntity<PortfolioResponse.PortfolioProjectResponse> importFromGithub(@RequestBody @Valid GithubImportRequest request) {
        log.info("GithubPortfolioController: Request received: Import Portfolio Project from GitHub: {}", request.getRepoUrl());
        return ResponseEntity.ok(githubPortfolioService.importFromGithub(request));
    }

    @GetMapping("/github-repos")
    @Operation(summary = "List & rank the student's GitHub repositories",
            description = "Uses the student's linked GitHub account to list their own repositories (public and private), ranked by a quality heuristic. No AI analysis is run at this stage.")
    public ResponseEntity<List<GithubRepoRankResponse>> listRankedRepos() {
        log.info("GithubPortfolioController: Request received: List & rank student's GitHub repositories");
        return ResponseEntity.ok(githubPortfolioService.listRankedRepos());
    }

    @PostMapping("/github-import-batch")
    @Operation(summary = "Import several selected GitHub repositories",
            description = "Runs AI analysis over each selected repository and returns the resulting (unsaved) project entries for the student to add to their portfolio.")
    public ResponseEntity<List<PortfolioResponse.PortfolioProjectResponse>> importBatch(@RequestBody @Valid GithubImportBatchRequest request) {
        log.info("GithubPortfolioController: Request received: Batch import {} GitHub repositories", request.getRepoUrls().size());
        return ResponseEntity.ok(githubPortfolioService.importBatch(request.getRepoUrls()));
    }
}
