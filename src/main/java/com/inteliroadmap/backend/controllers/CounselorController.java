package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.request.CreateFeedbackRequest;
import com.inteliroadmap.backend.domain.dto.request.GetSkillGapRequest;
import com.inteliroadmap.backend.domain.dto.request.ImportSkillsRequest;
import com.inteliroadmap.backend.domain.dto.request.ModifyFeedbackRequest;
import com.inteliroadmap.backend.domain.dto.response.CounselorResponse;
import com.inteliroadmap.backend.domain.dto.response.SkillResponse;
import com.inteliroadmap.backend.services.CounselorService;
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

@RestController
@RequestMapping("/counselor")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Counselor services", description = "Counselor services endpoints")
public class CounselorController {

    private final CounselorService counselorService;

    @GetMapping("/dashboard")
    @Operation(
            summary = "Get careers statistic",
            description = "Get number of students following a career"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Careers statistic retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CounselorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized or invalid token"
            )
    })
    public ResponseEntity<CounselorResponse> getCareerStatistics() {
        log.info("Careers statistic retrieval request received");
        return ResponseEntity.ok(counselorService.getCareerStatistics());
    }

    @GetMapping("/dashboard/{careerName}")
    @Operation(
            summary = "Get students skill gap of a career",
            description = "Get total of students missing skills by career"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Skill gap retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CounselorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized or invalid token"
            )
    })
    public ResponseEntity<CounselorResponse> getStudentsMissingSkills(@PathVariable String careerName) {
        log.info("Skill gap retrieval request received");
        return ResponseEntity.ok(counselorService.getStudentsMissingSkills(careerName));
    }

    @GetMapping("/feedback/{search}")
    @Operation(
            summary = "Get feedback created",
            description = "Get feedback created to a student by fullname of email"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Feedback retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CounselorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized or invalid token"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Students not found"
            )
    })
    public ResponseEntity<CounselorResponse> getFeedbackByStudent(@PathVariable String search) {
        log.info("Feedback retrieval request received");
        return ResponseEntity.ok(counselorService.getFeedbackByStudent(search));
    }

    @PostMapping("/dashboard/create-feedback")
    @Operation(
            summary = "Create feedback",
            description = "Create a feedback to a student"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Feedback package",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CounselorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized or invalid token"
            )
    })
    public ResponseEntity<CounselorResponse> createFeedback(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Create feedback payload",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateFeedbackRequest.class)
                    )
            )
            @RequestBody @Valid CreateFeedbackRequest request
    ) {
        log.info("Create feedback request received");
        return ResponseEntity.ok(counselorService.createFeedback(request));
    }

    @PatchMapping("/dashboard/modify-feedback")
    @Operation(
            summary = "Modify feedback",
            description = "Modify a feedback to a student"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Feedback package",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CounselorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized or invalid token"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Feedback not found"
            )
    })
    public ResponseEntity<CounselorResponse> modifyFeedback(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Modify feedback payload",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ModifyFeedbackRequest.class)
                    )
            )
            @RequestBody @Valid ModifyFeedbackRequest request
    ) {
        log.info("Modify feedback request received");
        return ResponseEntity.ok(counselorService.modifyFeedback(request));
    }
}
