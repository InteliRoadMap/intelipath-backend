package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.request.GithubImportRequest;
import com.inteliroadmap.backend.domain.entity.PortfolioProject;
import com.inteliroadmap.backend.services.GithubPortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity<com.inteliroadmap.backend.domain.dto.response.PortfolioResponse.PortfolioProjectResponse> importFromGithub(@RequestBody @Valid GithubImportRequest request) {
        log.info("GithubPortfolioController: Request received: Import Portfolio Project from GitHub: {}", request.getRepoUrl());
        return ResponseEntity.ok(githubPortfolioService.importFromGithub(request));
    }
}
