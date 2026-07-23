package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.request.GithubLinkRequest;
import com.inteliroadmap.backend.domain.dto.response.portfolio.GithubLinkResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.GithubLinkStartResponse;
import com.inteliroadmap.backend.services.GithubAccountLinkService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Dedicated "Connect GitHub" linking endpoints for the portfolio sync feature. Separate from
 * OAuth login so the access token is always bound to the signed-in student (never resolved by
 * GitHub email). See {@link GithubAccountLinkService}.
 */
@RestController
@RequestMapping("/api/v1/student/portfolio/github")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Student E-Portfolio", description = "Endpoints for managing student e-portfolios")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasRole('STUDENT')")
public class GithubAccountLinkController {

    private final GithubAccountLinkService githubAccountLinkService;

    @GetMapping("/link/start")
    @Operation(summary = "Begin connecting GitHub for repo sync",
            description = "Returns the GitHub authorize URL (requesting the repo scope) and a CSRF state to store and re-check on the callback.")
    public ResponseEntity<GithubLinkStartResponse> start() {
        log.info("GithubAccountLinkController: Request received: start GitHub link");
        return ResponseEntity.ok(githubAccountLinkService.start());
    }

    @PostMapping("/link")
    @Operation(summary = "Complete connecting GitHub for repo sync",
            description = "Exchanges the authorization code for an access token and stores it (encrypted) on the signed-in student.")
    public ResponseEntity<GithubLinkResponse> complete(@RequestBody @Valid GithubLinkRequest request) {
        log.info("GithubAccountLinkController: Request received: complete GitHub link");
        return ResponseEntity.ok(githubAccountLinkService.complete(request.getCode()));
    }

    @GetMapping("/link")
    @Operation(summary = "Check which GitHub account is connected",
            description = "Returns whether the signed-in student has a linked GitHub account, and if so its username and granted scopes.")
    public ResponseEntity<GithubLinkResponse> status() {
        log.info("GithubAccountLinkController: Request received: GitHub link status");
        return ResponseEntity.ok(githubAccountLinkService.status());
    }

    @DeleteMapping("/link")
    @Operation(summary = "Disconnect the linked GitHub account",
            description = "Revokes the authorization at GitHub and deletes the stored token. Already imported projects stay in the portfolio. "
                    + "To switch accounts, call this and then start the link flow again.")
    public ResponseEntity<GithubLinkResponse> unlink() {
        log.info("GithubAccountLinkController: Request received: unlink GitHub account");
        return ResponseEntity.ok(githubAccountLinkService.unlink());
    }
}
