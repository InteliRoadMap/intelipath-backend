package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.request.ImportSkillsRequest;
import com.inteliroadmap.backend.domain.dto.request.SetupStudentProfileRequest;
import com.inteliroadmap.backend.domain.dto.response.SkillResponse;
import com.inteliroadmap.backend.domain.dto.response.StudentResponse;
import com.inteliroadmap.backend.domain.dto.response.UserResponse;
import com.inteliroadmap.backend.services.SkillService;
import com.inteliroadmap.backend.services.StudentService;
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
 * Controller responsible for handling student-related API endpoints.
 * Provides functionality for profile setup and skill management.
 */
@RestController
@RequestMapping("/student")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Student services", description = "Student endpoints")
public class StudentController {

    private final StudentService studentService;
    private final SkillService skillService;

    /**
     * Sets up or updates the profile information for a student.
     *
     * @param setupStudentProfileRequest The payload containing student profile details
     * @return ResponseEntity containing the updated user information
     */
    @PatchMapping("/profile")
    @Operation(
            summary = "Setup student profile",
            description = "User setup student profile"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Setup profile successful",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found"
            )
    })
    public ResponseEntity<StudentResponse> setupStudentProfile(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Student Profile payload",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SetupStudentProfileRequest.class)
                    )
            )
            @RequestBody @Valid SetupStudentProfileRequest setupStudentProfileRequest
    ) {
        log.info("Student profile setup request received");
        // Delegate to StudentService to setup or update the student profile
        return ResponseEntity.ok(studentService.setupStudentProfile(setupStudentProfileRequest));
    }

    /**
     * Retrieves the information of a specific student.
     * @return ResponseEntity containing student's information
     */
    @GetMapping("/profile")
    @Operation(
            summary = "Get student profile",
            description = "Get student profile information"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Get student profile successful",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = StudentResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found"
            )
    })
    public ResponseEntity<StudentResponse> getStudentProfile() {
        log.info("Student profile retrieval request received");
        // Delegate to StudentService to fetch the student profile
        return ResponseEntity.ok(studentService.getStudentProfile());
    }

    /**
     * Retrieves the skills currently associated with a specific student.
     * @return ResponseEntity containing a list of the student's skills
     */
    @GetMapping("/skills")
    @Operation(
            summary = "Get student skills",
            description = "Get student skills"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Get student skills successful",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SkillResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Student skills not found"
            )
    })
    public ResponseEntity<SkillResponse> getStudentSkills() {
        log.info("Fetching student skills");
        // Delegate to SkillService to fetch skills associated with the student
        return ResponseEntity.ok(skillService.getStudentSkills());
    }

    /**
     * Retrieves a list of available skills filtered by a search query matching category or career.
     *
     * @param search The query string to search by (category or career)
     * @return ResponseEntity containing a list of matching skills
     */
    @GetMapping("/skills/{search}")
    @Operation(
            summary = "Search skills by category",
            description = "Search skills by category"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Search skills by category successful",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SkillResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "{category} skills not found"
            )
    })
    public ResponseEntity<SkillResponse> searchSkills(@PathVariable String search) {
        log.info("Fetching skills for search query: {}", search);
        // Delegate to SkillService to fetch skills by category or career
        return ResponseEntity.ok(skillService.searchSkills(search));
    }

    /**
     * Imports a list of selected skills and associates them with the student.
     *
     * @param importSkillsRequest The payload containing the student ID and selected skills
     * @return ResponseEntity containing the updated list of the student's skills
     */
    @PostMapping("/skills/select")
    @Operation(
            summary = "Selected student skills",
            description = "Student selects their current skills"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Skills imported successful",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SkillResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found"
            )
    })
    public ResponseEntity<SkillResponse> importStudentSkills(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Imported Student Skills payload",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ImportSkillsRequest.class)
                    )
            )
            @RequestBody @Valid ImportSkillsRequest importSkillsRequest
    ) {
        log.info("Importing selected skills for authenticated student");
        // Delegate to SkillService to process and save the selected skills
        return ResponseEntity.ok(skillService.importStudentSkills(importSkillsRequest));
    }
}
