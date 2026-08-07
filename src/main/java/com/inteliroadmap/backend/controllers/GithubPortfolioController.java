package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.response.portfolio.PortfolioProjectResponse;

import com.inteliroadmap.backend.domain.dto.request.GithubImportBatchRequest;
import com.inteliroadmap.backend.domain.dto.request.GithubImportRequest;
import com.inteliroadmap.backend.domain.dto.response.portfolio.GithubImportAuditResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.GithubRepoRankResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.PortfolioResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.RepoEvidenceResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.RepoSourcePlanResponse;
import com.inteliroadmap.backend.services.GithubPortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public ResponseEntity<PortfolioProjectResponse> importFromGithub(@RequestBody @Valid GithubImportRequest request) {
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
    public ResponseEntity<List<PortfolioProjectResponse>> importBatch(@RequestBody @Valid GithubImportBatchRequest request) {
        log.info("GithubPortfolioController: Request received: Batch import {} GitHub repositories", request.getRepoUrls().size());
        return ResponseEntity.ok(githubPortfolioService.importBatch(request.getRepoUrls()));
    }

    @PostMapping("/github-analysis-plan")
    @Operation(summary = "Preview the real source context selected for GitHub analysis",
            description = "Returns the production source paths the importer will read. It does not call AI or save evidence.")
    public ResponseEntity<List<RepoSourcePlanResponse>> planBatchAnalysis(
            @RequestBody @Valid GithubImportBatchRequest request) {
        return ResponseEntity.ok(githubPortfolioService.planBatchAnalysis(request.getRepoUrls()));
    }

    @GetMapping("/github-audit")
    @Operation(summary = "How the AI analysed one imported repository",
            description = "The files that were read and their sizes, the skill catalog and model used, "
                    + "and each matched skill with its current evidence status. 404 when this student "
                    + "has no audit for that repository.")
    // Logged like every other endpoint here. This one makes an invisible process
    // visible, and it was itself the only step in the flow leaving no trace: a click
    // that returned nothing and a click that never happened read identically.
    public ResponseEntity<GithubImportAuditResponse> getImportAudit(@RequestParam String repoUrl) {
        log.info("GithubPortfolioController: Request received: Import audit for {}", repoUrl);
        GithubImportAuditResponse audit = githubPortfolioService.getImportAudit(repoUrl);
        if (audit == null) {
            log.info("GithubPortfolioController: No import audit recorded for {}", repoUrl);
        }
        // 404 rather than an empty body: "imported before auditing existed" and "analysed
        // and found nothing" are different answers, and the UI shows different things.
        return audit == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(audit);
    }

    @GetMapping("/github-evidence")
    @Operation(summary = "What one repository is currently vouching for",
            description = "The skills this repository proved and their live evidence status. "
                    + "Answers 'what would I lose' before a portfolio project is deleted. "
                    + "An empty list is a valid answer, not a 404.")
    public ResponseEntity<RepoEvidenceResponse> getRepoEvidence(@RequestParam String repoUrl) {
        log.info("GithubPortfolioController: Request received: Evidence held by {}", repoUrl);
        return ResponseEntity.ok(githubPortfolioService.getRepoEvidence(repoUrl));
    }

    @DeleteMapping("/github-evidence")
    @Operation(summary = "Withdraw the skills one repository proved",
            description = "Deletes this repository's evidence rows and clears the verifier from any "
                    + "skill left with no other backing, which lowers the verified share the level "
                    + "is capped by. Only ever called because the student chose it — deleting a "
                    + "portfolio project on its own does not retract the work.")
    public ResponseEntity<RepoEvidenceResponse> withdrawRepoEvidence(@RequestParam String repoUrl) {
        log.info("GithubPortfolioController: Request received: Withdraw evidence from {}", repoUrl);
        return ResponseEntity.ok(githubPortfolioService.withdrawRepoEvidence(repoUrl));
    }
}
