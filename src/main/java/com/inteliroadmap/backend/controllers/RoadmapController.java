package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.request.UpdateNodeProgressRequest;
import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapNodeDto;
import com.inteliroadmap.backend.domain.dto.response.roadmap.SkillGapResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.StudentRoadmapResponse;
import com.inteliroadmap.backend.services.RoadmapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller responsible for handling roadmap and learning progress API endpoints.
 * Provides functionality to fetch career roadmaps, view specific learning nodes, and track user progress.
 */
@RestController
@RequestMapping("/api/v1/roadmaps")
@RequiredArgsConstructor
@Tag(name = "Roadmap", description = "Dynamic Roadmap APIs for Students")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasRole('STUDENT')")
public class RoadmapController {

    private final RoadmapService roadmapService;

    @GetMapping("/student")
    @Operation(summary = "Get student's roadmap", description = "Fetch the entire roadmap (Flat Array) of the logged-in student, combined with progress status to render the Map.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = StudentRoadmapResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Not logged in or invalid token"),
            @ApiResponse(responseCode = "404", description = "Not Found - Student not found or student does not have a Career")
    })
    public ResponseEntity<StudentRoadmapResponse> getStudentRoadmap() {
        return ResponseEntity.ok(roadmapService.getStudentRoadmap());
    }

    @GetMapping("/{careerId}")
    @Operation(summary = "Get career roadmap template", description = "Fetch the base roadmap template of a specific Career (without student progress).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = StudentRoadmapResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Not Found - Career not found")
    })
    public ResponseEntity<StudentRoadmapResponse> getCareerRoadmapTemplate(
            @PathVariable UUID careerId) {
        return ResponseEntity.ok(roadmapService.getCareerRoadmapTemplate(careerId));
    }

    @GetMapping("/{careerId}/progress")
    @Operation(summary = "Get career progress", description = "Fetch the overall progress percentage of a student for a Career.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful",
                    content = @Content(mediaType = "application/json", schema = @Schema(type = "integer"))),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Integer> getCareerProgress(
            @PathVariable UUID careerId,
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(roadmapService.getCareerProgress(careerId, authHeader));
    }

    @GetMapping("/nodes/{nodeId}")
    @Operation(summary = "Get node detail", description = "Fetch details of a single Node in the roadmap.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoadmapNodeDto.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Not Found - Node not found")
    })
    public ResponseEntity<RoadmapNodeDto> getNodeDetail(
            @PathVariable UUID nodeId) {
        return ResponseEntity.ok(roadmapService.getNodeDetail(nodeId));
    }

    @PutMapping("/nodes/progress")
    @Operation(summary = "Update node progress", description = "Update the status of a Node (e.g., 'completed', 'in_progress').")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful"),
            @ApiResponse(responseCode = "400", description = "Bad Request - Invalid payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Not Found - Node or Student not found")
    })
    public ResponseEntity<Void> updateNodeProgress(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Update Node progress request payload",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UpdateNodeProgressRequest.class)
                    )
            )
            @Valid @RequestBody UpdateNodeProgressRequest request,
            @RequestHeader("Authorization") String authHeader) {
        roadmapService.updateNodeProgress(request, authHeader);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/skills/compare")
    @Operation(summary = "Compare skills", description = "Compare the student's current skills with the skills required by the Career to find Skill Gaps.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = SkillGapResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Not Found - Student has not selected a Career")
    })
    public ResponseEntity<SkillGapResponse> compareSkills(
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(roadmapService.compareSkills(authHeader));
    }
}
