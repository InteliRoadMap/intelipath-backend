package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapRecommendationDecisionResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapRecommendationResponse;
import com.inteliroadmap.backend.services.RoadmapPersonalizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Controller - Roadmap Personalization API Endpoints
 * Provides endpoints:
 * - GET  /api/v1/roadmaps/recommendations - List pending recommendations for the current student
 * - POST /api/v1/roadmaps/recommendations/generate - Generate new recommendations from skills and evidence
 * - POST /api/v1/roadmaps/recommendations/{recommendationId}/accept - Apply a recommendation
 * - POST /api/v1/roadmaps/recommendations/{recommendationId}/reject - Decline a recommendation
 */
@RestController
@RequestMapping("/api/v1/roadmaps/recommendations")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('STUDENT')")
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "Roadmap Recommendations", description = "AI-assisted roadmap personalization for students")
public class RoadmapRecommendationController {

    private final RoadmapPersonalizationService roadmapPersonalizationService;

    @GetMapping
    @Operation(
            summary = "Get pending roadmap recommendations",
            description = "Returns the current student's PENDING recommendations, newest first, "
                    + "including the node-level actions each one would apply"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Pending recommendations retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RoadmapRecommendationResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Caller is not a student"),
            @ApiResponse(responseCode = "404", description = "Student profile not found")
    })
    public ResponseEntity<List<RoadmapRecommendationResponse>> getPendingRecommendations() {
        log.info("RoadmapRecommendationController: Pending recommendations request received");
        return ResponseEntity.ok(roadmapPersonalizationService.getPendingRecommendationsForCurrentStudent());
    }

    @PostMapping("/generate")
    @Operation(
            summary = "Generate roadmap recommendations",
            description = "Analyzes the student's profile skills and skill evidence, then creates a PENDING "
                    + "recommendation for roadmap nodes that can be skipped. Returns an empty list when "
                    + "there is nothing new to recommend. Progress is never changed until the student accepts."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Generation completed (possibly with no new recommendations)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RoadmapRecommendationResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Student has not selected a career path"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Caller is not a student"),
            @ApiResponse(responseCode = "404", description = "Student profile not found")
    })
    public ResponseEntity<List<RoadmapRecommendationResponse>> generateRecommendations() {
        log.info("RoadmapRecommendationController: Generate recommendations request received");
        return ResponseEntity.ok(roadmapPersonalizationService.generateRecommendationsForCurrentStudent());
    }

    @PostMapping("/{recommendationId}/accept")
    @Operation(
            summary = "Accept a roadmap recommendation",
            description = "Marks the recommended nodes as COMPLETED, syncs fully-completed skills into the "
                    + "student's profile, links the supporting evidence, and returns the recalculated progress"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Recommendation accepted and roadmap progress updated",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RoadmapRecommendationDecisionResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Recommendation does not belong to the current student"),
            @ApiResponse(responseCode = "404", description = "Recommendation not found"),
            @ApiResponse(responseCode = "409", description = "Recommendation has already been decided")
    })
    public ResponseEntity<RoadmapRecommendationDecisionResponse> acceptRecommendation(
            @PathVariable UUID recommendationId
    ) {
        log.info("RoadmapRecommendationController: Accept recommendation request received. recommendationId: {}", recommendationId);
        return ResponseEntity.ok(roadmapPersonalizationService.acceptRecommendation(recommendationId));
    }

    @PostMapping("/{recommendationId}/reject")
    @Operation(
            summary = "Reject a roadmap recommendation",
            description = "Marks the recommendation as REJECTED without changing any progress or skills"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Recommendation rejected",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RoadmapRecommendationDecisionResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Recommendation does not belong to the current student"),
            @ApiResponse(responseCode = "404", description = "Recommendation not found"),
            @ApiResponse(responseCode = "409", description = "Recommendation has already been decided")
    })
    public ResponseEntity<RoadmapRecommendationDecisionResponse> rejectRecommendation(
            @PathVariable UUID recommendationId
    ) {
        log.info("RoadmapRecommendationController: Reject recommendation request received. recommendationId: {}", recommendationId);
        return ResponseEntity.ok(roadmapPersonalizationService.rejectRecommendation(recommendationId));
    }
}
