package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.request.PortfolioUpsertRequest;
import com.inteliroadmap.backend.domain.dto.request.RequestReviewRequest;
import com.inteliroadmap.backend.domain.dto.request.UpdateSlugRequest;
import com.inteliroadmap.backend.domain.dto.response.portfolio.PortfolioResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.PortfolioAboutDraftResponse;
import com.inteliroadmap.backend.services.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/student/portfolio")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Student E-Portfolio", description = "Endpoints for managing student e-portfolios")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasRole('STUDENT')")
public class PortfolioController {

    private final PortfolioService portfolioService;

    @GetMapping("/me")
    @Operation(summary = "Get E-Portfolio Data", description = "Fetch all profile data, configs, skills, projects, and education for the current student")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation", 
                content = @Content(schema = @Schema(implementation = PortfolioResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<PortfolioResponse> getPortfolio() {
        log.info("PortfolioController: Request received: Get Student Portfolio");
        return ResponseEntity.ok(portfolioService.getPortfolio());
    }

    @PutMapping("/me")
    @Operation(summary = "Auto-save E-Portfolio", description = "Upsert portfolio configurations, projects, and education")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Saved successfully", 
                content = @Content(schema = @Schema(implementation = PortfolioResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<PortfolioResponse> upsertPortfolio(
            @RequestBody @Valid PortfolioUpsertRequest request) {
        log.info("PortfolioController: Request received: Upsert Student Portfolio");
        return ResponseEntity.ok(portfolioService.upsertPortfolio(request));
    }

    @PostMapping("/about/ai-draft")
    @Operation(summary = "Generate an AI About Me draft", description = "Returns a preview from existing portfolio facts and never saves it")
    public ResponseEntity<PortfolioAboutDraftResponse> generateAboutDraft() {
        return ResponseEntity.ok(portfolioService.generateAboutDraft());
    }

    @PostMapping("/request-review")
    @Operation(summary = "Request Portfolio Review", description = "Send a review request to a mentor by their email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Review request sent successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid payload or request already exists"),
            @ApiResponse(responseCode = "404", description = "Mentor not found")
    })
    public ResponseEntity<Map<String, Object>> requestReview(
            @RequestBody @Valid RequestReviewRequest request) {
        log.info("PortfolioController: Request received: Request Portfolio Review to {}", request.getEmail());
        portfolioService.requestReview(request);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Review request sent successfully."
        ));
    }
}
