package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.request.ImportSkillsRequest;
import com.inteliroadmap.backend.domain.dto.request.SetupStudentProfileRequest;
import com.inteliroadmap.backend.domain.dto.request.TargetCareerRequest;
import com.inteliroadmap.backend.domain.dto.response.SkillResponse;
import com.inteliroadmap.backend.domain.dto.response.StudentResponse;
import com.inteliroadmap.backend.services.SkillService;
import com.inteliroadmap.backend.services.StudentService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller responsible for handling student-related API endpoints.
 * Provides functionality for profile setup and skill management.
 */
@RestController
@RequestMapping("/api/v1/student")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Student services", description = "Student endpoints")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasRole('STUDENT')")
public class StudentController {

    private final StudentService studentService;
    private final SkillService skillService;

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
                    responseCode = "401",
                    description = "Unauthorized or invalid access token"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found"
            )
    })
    public ResponseEntity<StudentResponse> getStudentProfile() {
        log.info("StudentController: Student profile retrieval request received");
        return ResponseEntity.ok(studentService.getStudentProfile());
    }

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
                            schema = @Schema(implementation = StudentResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid student profile payload"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized or invalid access token"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User or career role not found"
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
        log.info("StudentController: Student profile setup request received");
        return ResponseEntity.ok(studentService.setupStudentProfile(setupStudentProfileRequest));
    }

    @PostMapping(value = "/profile/transcript", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload student transcript for AI processing",
            description = "Upload transcript PDF, which will be processed by RAG for personalized Virtual Mentor insights"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Transcript uploaded and processed successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = StudentResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid file or file type"
            )
    })
    public ResponseEntity<StudentResponse> uploadTranscript(
            @RequestParam("file") MultipartFile file
    ) {
        log.info("StudentController: Student transcript upload request received");
        return ResponseEntity.ok(studentService.uploadTranscript(file));
    }

    @Deprecated
    @PutMapping("/target-career")
    @Operation(
            summary = "Update target career (DEPRECATED)",
            description = "DEPRECATED: Please use PATCH /api/v1/student/profile instead. Update the authenticated student's target career using a database career UUID",
            deprecated = true
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Target career updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = StudentResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid career ID"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized or invalid access token"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Student or career role not found"
            )
    })
    public ResponseEntity<StudentResponse> updateTargetCareer(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Target career request payload",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TargetCareerRequest.class)
                    )
            )
            @RequestBody @Valid TargetCareerRequest request
    ) {
        log.warn("StudentController: DEPRECATED API CALLED: PUT /target-career. Please migrate to PATCH /profile.");
        return ResponseEntity.ok(studentService.updateTargetCareer(request.getCareerId()));
    }


    /**
     * Retrieves the authenticated student's selected skills and all available skills.
     *
     * @return response containing selected skills and all skills in the database
     */
    @GetMapping("/skills")
    @Operation(
            summary = "Get selected and available skills",
            description = "Get the authenticated student's selected skills and all available skills"
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
                    responseCode = "401",
                    description = "Unauthorized or invalid access token"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Student skills not found"
            )
    })
    public ResponseEntity<SkillResponse> getStudentSkills() {
        log.info("StudentController: Fetching selected and available skills for the authenticated student");
        return ResponseEntity.ok(skillService.getStudentSkills());
    }

    /**
     * Searches available skills by skill name without case sensitivity.
     *
     * @param search skill name fragment to search for
     * @return response containing matching skills
     */
    @GetMapping("/skills/search")
    @Operation(
            summary = "Search skills by name",
            description = "Search skills by skill name without case sensitivity"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Skill search completed successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SkillResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized or invalid access token"
            )
    })
    public ResponseEntity<SkillResponse> searchSkills(@RequestParam String keyword) {
        log.info("StudentController: Searching skills by name: {}", keyword);

        return ResponseEntity.ok(skillService.searchSkills(keyword));
    }

    /**
     * Imports a list of selected skills and associates them with the student.
     *
     * @param importSkillsRequest The payload containing selected skill IDs
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
                    responseCode = "400",
                    description = "Invalid skill selection payload"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized or invalid access token"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User or skill not found"
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
        log.info("StudentController: Importing selected skills for authenticated student");
        return ResponseEntity.ok(skillService.importStudentSkills(importSkillsRequest));
    }
}
