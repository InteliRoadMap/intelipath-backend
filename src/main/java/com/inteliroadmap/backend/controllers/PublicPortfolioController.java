package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.response.portfolio.PortfolioResponse;
import com.inteliroadmap.backend.services.PortfolioService;
import com.inteliroadmap.backend.services.GithubPortfolioService;
import com.inteliroadmap.backend.domain.dto.response.portfolio.GithubImportAuditResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/public-portfolio")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Public E-Portfolio", description = "Public endpoints for viewing student e-portfolios without authentication")
public class PublicPortfolioController {

    private final PortfolioService portfolioService;
    private final GithubPortfolioService githubPortfolioService;

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Get Public E-Portfolio Data by Slug", description = "Fetch all public profile data, configs, skills, projects, and education for a student by their portfolio slug")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation", 
                content = @Content(schema = @Schema(implementation = PortfolioResponse.class))),
            @ApiResponse(responseCode = "404", description = "Portfolio or User not found")
    })
    public ResponseEntity<PortfolioResponse> getPortfolioBySlug(@PathVariable String slug) {
        log.info("PublicPortfolioController: Request received: Get Public Portfolio for slug '{}'", slug);
        return ResponseEntity.ok(portfolioService.getPortfolioBySlug(slug));
    }

    @GetMapping("/slug/{slug}/project-evidence")
    @Operation(summary = "Get sanitized AI evidence for a displayed portfolio project",
            description = "Public, read-only proof. Source contents, tokens, author login and commit messages are never returned.")
    public ResponseEntity<GithubImportAuditResponse> getProjectEvidence(
            @PathVariable String slug, @RequestParam String repoUrl) {
        GithubImportAuditResponse audit = githubPortfolioService.getPublicImportAudit(slug, repoUrl);
        return audit == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(audit);
    }
}
