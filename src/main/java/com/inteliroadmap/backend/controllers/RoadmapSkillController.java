package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.response.roadmap.SkillResponse;
import com.inteliroadmap.backend.services.StudentService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/roadmap/skills")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Roadmap Skills", description = "Student roadmap skill comparison endpoints")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasRole('STUDENT')")
public class RoadmapSkillController {

    private final StudentService studentService;

    /**
     * Compare authenticated student's selected skills with required career skills.
     *
     * @return skill comparison response
     */
    @PostMapping("/compare")
    @Operation(
            summary = "Compare student skills",
            description = "Compare the authenticated student's selected skills with the skills required by the target career"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Student skills compared successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SkillResponse.class)
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
    public ResponseEntity<SkillResponse> compareSkills() {
        log.info("RoadmapSkillController: Compare skills request received");
        return ResponseEntity.ok(studentService.compareCurrentStudentSkills());
    }
}
