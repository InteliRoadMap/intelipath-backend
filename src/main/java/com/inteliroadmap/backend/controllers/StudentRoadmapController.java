package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.response.StudentRoadmapResponse;
import com.inteliroadmap.backend.services.RoadmapService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/roadmap")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Student Roadmap", description = "Authenticated student roadmap endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class StudentRoadmapController {

    private final RoadmapService roadmapService;

    /**
     * Gets the authenticated student's current target roadmap.
     *
     * @return frontend contract response with roadmap progress and nodes
     */
    @GetMapping("/student")
    @Operation(
            summary = "Get student roadmap",
            description = "Get the authenticated student's target roadmap, progress, nodes, and learning resources"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Student roadmap fetched successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = StudentRoadmapResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized or invalid access token"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Student or target career not found"
            )
    })
    public ResponseEntity<StudentRoadmapResponse> getStudentRoadmap() {
        log.info("Student Roadmap API: Get student roadmap request received");
        return ResponseEntity.ok(roadmapService.getStudentRoadmap());
    }
}
