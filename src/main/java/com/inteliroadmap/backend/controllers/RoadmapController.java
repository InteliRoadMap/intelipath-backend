package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.response.RoadmapResponse;
import com.inteliroadmap.backend.services.RoadmapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller responsible for handling roadmap and learning progress API endpoints.
 * Provides functionality to fetch career roadmaps, view specific learning nodes, and track user progress.
 */
@RestController
@RequestMapping("/roadmap")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Roadmap services", description = "Roadmap endpoints")
public class RoadmapController {

    private final RoadmapService roadmapService;

    /**
     * Retrieves the complete learning roadmap for a specific career.
     *
     * @param careerId The unique identifier (UUID) of the career.
     * @return ResponseEntity containing a RoadmapResponse with the associated career and skill nodes.
     */
    @GetMapping("/{career_id}")
    @Operation(
            summary = "Get Roadmap",
            description = "Get available roadmaps by career id"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Roadmap fetched successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RoadmapResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No roadmap available"
            )
    })
    public ResponseEntity<RoadmapResponse> getRoadmap(@PathVariable("career_id") UUID careerId) {
        log.info("Fetching Roadmaps from database");
        return ResponseEntity.ok(roadmapService.getRoadmap(careerId));
    }

    /**
     * Retrieves detailed information about a specific learning node (skill node).
     *
     * @param nodeId The unique identifier (UUID) of the skill node.
     * @return ResponseEntity containing a RoadmapResponse with the node's specific details.
     */
    @GetMapping("/node/{node_id}")
    @Operation(
            summary = "Get node info",
            description = "Get node info by id"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Node info fetched successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RoadmapResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No node available"
            )
    })
    public ResponseEntity<RoadmapResponse> getNode(@PathVariable("node_id") UUID nodeId) {
        log.info("Updating user's study progress");
        return ResponseEntity.ok(roadmapService.getNode(nodeId));
    }

    /**
     * Updates the authenticated user's progress, marking a specific skill node as finished.
     *
     * @param nodeId The unique identifier (UUID) of the skill node to mark as completed.
     * @return ResponseEntity containing a RoadmapResponse confirming the progress update.
     */
    @PatchMapping("/node/{node_id}/progress")
    @Operation(
            summary = "Update Node status",
            description = "Update Node status after finishing it"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Node status update successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RoadmapResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No node available"
            )
    })
    public ResponseEntity<RoadmapResponse> updateNodeProgress(@PathVariable("node_id") UUID nodeId) {
        log.info("Updating user's study progress");
        return ResponseEntity.ok(roadmapService.updateNodeProgress(nodeId));
    }

}
