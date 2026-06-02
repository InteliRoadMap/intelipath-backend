package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.request.CompareStRmSkillRequest;
import com.inteliroadmap.backend.domain.dto.request.ImportSkillsRequest;
import com.inteliroadmap.backend.domain.dto.request.UpdateUserProgressRequest;
import com.inteliroadmap.backend.domain.dto.response.RoadmapResponse;
import com.inteliroadmap.backend.domain.dto.response.SkillResponse;
import com.inteliroadmap.backend.services.RoadmapService;
import com.inteliroadmap.backend.services.SkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
    private final SkillService skillService;

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
        log.info("Fetching roadmap for career ID: {}", careerId);
        // Delegate to RoadmapService to retrieve the career's full learning roadmap
        return ResponseEntity.ok(roadmapService.getRoadmap(careerId));
    }

    @GetMapping("/{career_id}/progress")
    @Operation(
            summary = "Get Roadmap total progress",
            description = "Get Roadmap total progress by career id"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Roadmap total progress fetched successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RoadmapResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No progress available"
            )
    })
    public ResponseEntity<RoadmapResponse> getRoadmapProgress(@PathVariable("career_id") UUID careerId) {
        log.info("Fetching total progress for career ID: {}", careerId);
        // Delegate to RoadmapService to retrieve the career's full learning roadmap
        return ResponseEntity.ok(roadmapService.getRoadmapProgress(careerId));
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
        log.info("Fetching details for skill node ID: {}", nodeId);
        // Delegate to RoadmapService to fetch specific node information
        return ResponseEntity.ok(roadmapService.getNode(nodeId));
    }

    /**
     * Updates the authenticated user's progress, marking a specific skill node as finished.
     *
     * @param request The payload containing the student ID and the target node ID.
     * @return ResponseEntity containing a RoadmapResponse confirming the progress update.
     */
    @PatchMapping("/node/progress")
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
    public ResponseEntity<RoadmapResponse> updateNodeProgress(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Update Node progress request payload",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UpdateUserProgressRequest.class)
                    )
            )
            @RequestBody @Valid UpdateUserProgressRequest request
    ) {
        log.info("Updating progress for skill node ID: {}", request.getNodeId());
        // Delegate to RoadmapService to mark the specified node as completed
        return ResponseEntity.ok(roadmapService.updateNodeProgress(request));
    }

    /**
     * Compares the skills required by a roadmap (career) against the student's current skills to identify gaps.
     *
     * @param request The payload containing the student ID and the target career ID
     * @return ResponseEntity containing the student's skills, the career's required skills, and the missing skills
     */
    @PostMapping("/skills/compare")
    @Operation(
            summary = "Compare Roadmap required skills with student skills",
            description = "Get Roadmap required skills and compare with student existing skills"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Skills successfully compared",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SkillResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Student or Career not found"
            )
    })
    public ResponseEntity<SkillResponse> compareWithStudentSkills(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Compare student skill request payload",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CompareStRmSkillRequest.class)
                    )
            )
    @RequestBody @Valid CompareStRmSkillRequest request
    ) {
        log.info("Comparing roadmap required skills for career ID: {} against student ID: {}", request.getCareerId(), request.getStudentId());
        // Delegate to SkillService to compute the skill gap and return the comparison
        return ResponseEntity.ok(skillService.compareWithStudentSkills(request));
    }

}
