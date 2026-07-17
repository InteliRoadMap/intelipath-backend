package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.response.roadmap.CareerResponse;
import com.inteliroadmap.backend.services.CareerService;
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

import java.util.List;
import java.util.UUID;

/**
 * Controller responsible for handling career-related API endpoints.
 * Provides functionality to fetch available career roles and their specific skill requirements.
 */
@RestController
@RequestMapping("/api/v1/careers")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Career services", description = "Career endpoints")
public class CareerController {

    private final CareerService careerService;

    /**
     * Retrieves a list of all available career roles in the system.
     *
     * @return ResponseEntity containing a list of CareerResponse objects with basic career details.
     */
    @GetMapping
    @Operation(
            summary = "Get Career roles",
            description = "Get list of available career roles"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Career roles fetched successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CareerResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized or invalid access token"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No Career role available"
            )
    })
    public ResponseEntity<List<CareerResponse>> getAllCareers() {
        log.info("CareerController: Fetching all career roles");
        // Delegate to CareerService to retrieve all available careers
        return ResponseEntity.ok(careerService.getAllCareers());
    }

    /**
     * Retrieves the specific skill node requirements for a given career.
     *
     * @param careerId The unique identifier (UUID) of the career role.
     * @return ResponseEntity containing a CareerResponse wrapped with the list of required SkillNodes.
     */
    @GetMapping("/{career_id}/requirements")
    @Operation(
            summary = "Get Career requirements",
            description = "Get Career requirements"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Career requirements fetched successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CareerResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized or invalid access token"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No Career requirement available"
            )
    })
    public ResponseEntity<CareerResponse> getCareerRequirements(@PathVariable("career_id") UUID careerId) {
        log.info("CareerController: Fetching requirements for career role: {}", careerId);
        // Delegate to CareerService to fetch skill node requirements for the specified career
        return ResponseEntity.ok(careerService.getCareerRequirements(careerId));
    }
}

