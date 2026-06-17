package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.request.CreateFeedbackRequest;
import com.inteliroadmap.backend.domain.dto.request.ModifyFeedbackRequest;
import com.inteliroadmap.backend.domain.dto.request.UpdateProfileRequest;
import com.inteliroadmap.backend.domain.dto.response.CounselorResponse;
import com.inteliroadmap.backend.domain.dto.response.UpdateProfileResponse;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/counselor")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Counselor services", description = "Counselor services endpoints")
@PreAuthorize("hasRole('COUNSELOR')")
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

    @GetMapping("/dashboard/missing-skills/{careerName}")
    @Operation(
            summary = "Get students skill gap of a career",
            description = "Get total of students missing skills by career or all careers if omitted"
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
                    responseCode = "400",
                    description = "Multiple careers found. Please type a more specific search."
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized or invalid token"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No career found matching your search."
            )
    })
    public ResponseEntity<CounselorResponse> getStudentsMissingSkills(@PathVariable String careerName) {
        log.info("Skill gap retrieval request received");
        return ResponseEntity.ok(counselorService.getStudentsMissingSkills(careerName));
    }

    @GetMapping("/dashboard/feedback/me")
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
    public ResponseEntity<CounselorResponse> getFeedbackSentToMe() {
        log.info("Feedback retrieval request received");
        return ResponseEntity.ok(counselorService.getAllFeedbacksSentToMe());
    }

    @GetMapping({"/feedback/students", "/feedback/students/{search}"})
    @Operation(
            summary = "Get students info",
            description = "Get all students info"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Students retrieved successfully",
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
    public ResponseEntity<CounselorResponse> getStudentInfos(@PathVariable(required = false) String search) {
        log.info("Assigned students info retrieval request received");
        return ResponseEntity.ok(counselorService.getStudentInfos(search));
    }

    @GetMapping("/feedback/{studentId}")
    @Operation(
            summary = "Get feedback history of a student",
            description = "Get feedback history between a student and a counselor"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Feedbacks retrieved successfully",
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
    public ResponseEntity<CounselorResponse> getFeedbacksHistoryWithStudent(@PathVariable UUID studentId) {
        log.info("Get feedback history request received");
        return ResponseEntity.ok(counselorService.getFeedbacksHistoryWithStudent(studentId));
    }

    @PostMapping("/feedback/create")
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

//    @PatchMapping("/dashboard/modify-feedback")
//    @Operation(
//            summary = "Modify feedback",
//            description = "Modify a feedback to a student"
//    )
//    @ApiResponses(value = {
//            @ApiResponse(
//                    responseCode = "200",
//                    description = "Feedback package",
//                    content = @Content(
//                            mediaType = "application/json",
//                            schema = @Schema(implementation = CounselorResponse.class)
//                    )
//            ),
//            @ApiResponse(
//                    responseCode = "401",
//                    description = "Unauthorized or invalid token"
//            ),
//            @ApiResponse(
//                    responseCode = "404",
//                    description = "Feedback not found"
//            )
//    })
//    public ResponseEntity<CounselorResponse> modifyFeedback(
//            @io.swagger.v3.oas.annotations.parameters.RequestBody(
//                    description = "Modify feedback payload",
//                    required = true,
//                    content = @Content(
//                            mediaType = "application/json",
//                            schema = @Schema(implementation = ModifyFeedbackRequest.class)
//                    )
//            )
//            @RequestBody @Valid ModifyFeedbackRequest request
//    ) {
//        log.info("Modify feedback request received");
//        return ResponseEntity.ok(counselorService.modifyFeedback(request));
//    }

    @GetMapping("/me/profile")
    @Operation(
            summary = "Get Counselor profile",
            description = "Get Counselor profile with new info"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Counselor profile package",
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
    public ResponseEntity<UpdateProfileResponse> getProfile() {
        log.info("Get Counselor profile request received");
        return ResponseEntity.ok(counselorService.getProfile());
    }

    @PatchMapping("/profile")
    @Operation(
            summary = "Update Counselor profile",
            description = "Update Counselor profile with new info"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "User profile package",
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
    public ResponseEntity<UpdateProfileResponse> updateProfile(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Update user info payload",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UpdateProfileRequest.class)
                    )
            )
            @RequestBody @Valid UpdateProfileRequest request
    ) {
        log.info("Update profile request received");
        return ResponseEntity.ok(counselorService.updateProfile(request));
    }
}
