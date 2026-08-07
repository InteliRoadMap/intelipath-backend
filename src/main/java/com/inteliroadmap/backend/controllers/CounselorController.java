package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.response.counselor.FeedbackResponse;

import com.inteliroadmap.backend.domain.dto.request.*;
import com.inteliroadmap.backend.domain.dto.response.counselor.CurriculumResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

import com.inteliroadmap.backend.domain.dto.response.counselor.CounselorDashboardResponse;
import com.inteliroadmap.backend.domain.dto.response.counselor.CounselorFeedbackResponse;
import com.inteliroadmap.backend.domain.dto.response.student.UpdateProfileResponse;
import com.inteliroadmap.backend.services.CounselorService;
import com.inteliroadmap.backend.services.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.UUID;

/**
 * REST Controller for Counselor services.
 * Provides endpoints for managing counselor profiles, tracking student statistics,
 * and managing feedbacks sent to students.
 */
@RestController
@RequestMapping("/api/v1/counselor")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Counselor services", description = "Counselor services endpoints")
@PreAuthorize("hasRole('COUNSELOR')")
public class CounselorController {

    private final CounselorService counselorService;
    private final FeedbackService feedbackService;

    /**
     * Retrieves career statistics for the counselor's dashboard.
     * Includes the number of students currently following various career paths.
     *
     * @return ResponseEntity containing a CounselorResponse with career statistics.
     */
    @GetMapping("/dashboard")
    @Operation(summary = "Get careers statistic", description = "Get number of students following a career")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Careers statistic retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CounselorDashboardResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized or invalid token")
    })
    public ResponseEntity<CounselorDashboardResponse> getCareerStatistics() {
        log.info("CounselorController: Careers statistic retrieval request received");
        return ResponseEntity.ok(counselorService.getCareerStatistics());
    }

    /**
     * Retrieves the skill gap statistics for students regarding a specific career.
     * Shows how many students are missing required skills for the given career name.
     *
     * @param careerName the name of the career to query
     * @return ResponseEntity containing a CounselorResponse with skill gap data.
     */
    @GetMapping("/dashboard/missing-skills/{careerName}")
    @Operation(summary = "Get students skill gap of a career", description = "Get total of students missing skills by career or all careers if omitted")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Skill gap retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CounselorDashboardResponse.class))),
            @ApiResponse(responseCode = "400", description = "Multiple careers found. Please type a more specific search."),
            @ApiResponse(responseCode = "401", description = "Unauthorized or invalid token"),
            @ApiResponse(responseCode = "404", description = "No career found matching your search.")
    })
    public ResponseEntity<CounselorDashboardResponse> getStudentsMissingSkills(@PathVariable String careerName) {
        log.info("CounselorController: Skill gap retrieval request received");
        return ResponseEntity.ok(counselorService.getStudentsMissingSkills(careerName));
    }

    /**
     * Retrieves all feedback created by the currently authenticated counselor.
     *
     * @return ResponseEntity containing a CounselorResponse with the feedback list.
     */
    @GetMapping("/dashboard/feedback/me")
    @Operation(summary = "Get feedback created", description = "Get feedback created to a student")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Feedbacks retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CounselorDashboardResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized or invalid token"),
            @ApiResponse(responseCode = "404", description = "Students not found")
    })
    public ResponseEntity<CounselorDashboardResponse> getFeedbackSentByMe() {
        log.info("CounselorController: Feedback retrieval request received");
        return ResponseEntity.ok(counselorService.getAllFeedbacksSentByMe());
    }

    /**
     * Retrieves paginated information of students assigned to or viewable by the counselor.
     * Supports optional searching by student name or email.
     *
     * @param page the page number (0-indexed)
     * @param size the size of the page
     * @param search an optional search term to filter students
     * @return ResponseEntity containing a CounselorResponse with paginated student info.
     */
    @GetMapping("/feedback/students")
    @Operation(summary = "Get students info", description = "Get all students info")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Students retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CounselorFeedbackResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized or invalid token")
    })
    public ResponseEntity<CounselorFeedbackResponse> getStudentInfos(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String career) {
        log.info("CounselorController: Assigned students info retrieval request received");
        return ResponseEntity.ok(counselorService.getStudentInfos(search, page, size, career));
    }

    /**
     * Retrieves detailed statistics and all related feedbacks for a specific student.
     *
     * @param studentId the UUID of the target student
     * @return ResponseEntity containing a CounselorResponse with the student's data.
     */
    @GetMapping("/feedback/student/info/{studentId}")
    @Operation(summary = "Get stats and feedbacks info", description = "Get student statistic and feedbacks info")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Statistic And Feedback retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CounselorFeedbackResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized or invalid token")
    })
    public ResponseEntity<CounselorFeedbackResponse> getStudentStatisticAndFeedback(@PathVariable UUID studentId) {
        log.info("CounselorController: Get statistic and feedback request received");
        return ResponseEntity.ok(counselorService.getStudentStatisticAndFeedback(studentId));
    }

    /**
     * Retrieves the profile information of the currently authenticated counselor.
     *
     * @return ResponseEntity containing the counselor's UpdateProfileResponse.
     */
    @GetMapping("/me/profile")
    @Operation(summary = "Get Counselor profile", description = "Get Counselor profile with new info")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Counselor profile package", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UpdateProfileResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized or invalid token")
    })
    public ResponseEntity<UpdateProfileResponse> getProfile() {
        log.info("CounselorController: Get Counselor profile request received");
        return ResponseEntity.ok(counselorService.getProfile());
    }

    /**
     * Updates the profile information of the currently authenticated counselor.
     *
     * @param request the payload containing the updated profile data
     * @return ResponseEntity containing the updated UpdateProfileResponse.
     */
    @PatchMapping("/me/profile")
    @Operation(summary = "Update Counselor profile", description = "Update Counselor profile with new info"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User profile package", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UpdateProfileResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized or invalid token")
    })
    public ResponseEntity<UpdateProfileResponse> updateProfile(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Update user info payload", required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UpdateProfileRequest.class)
                    )
            )
            @RequestBody @Valid UpdateProfileRequest request
    ) {
        log.info("CounselorController: Update profile request received");
        return ResponseEntity.ok(counselorService.updateProfile(request));
    }

    /**
     * Creates a new feedback entry from the counselor to a specific student.
     *
     * @param request the payload containing receiver details and feedback content
     * @return ResponseEntity containing the generated FeedbackResponse.
     */
    @PostMapping("/feedback/create")
    @Operation(summary = "Create feedback", description = "Create a feedback to a student")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Feedback package", content = @Content(mediaType = "application/json", schema = @Schema(implementation = FeedbackResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized or invalid token")
    })
    public ResponseEntity<FeedbackResponse> createFeedback(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Create feedback payload", required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateFeedbackRequest.class)
                    )
            )
            @RequestPart("request") @Valid CreateFeedbackRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        log.info("CounselorController: Create feedback request received");
        return ResponseEntity.ok(feedbackService.createFeedback(request, files));
    }

    /**
     * Modifies an existing feedback entry that was previously sent to a student.
     *
     * @param request the payload containing the feedback ID and the modified content
     * @return ResponseEntity containing the updated FeedbackResponse.
     */
    @PatchMapping("/feedback/modify")
    @Operation(summary = "Modify feedback", description = "Modify a feedback to a student")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Feedback package", content = @Content(mediaType = "application/json", schema = @Schema(implementation = FeedbackResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized or invalid token"),
            @ApiResponse(responseCode = "404", description = "Feedback not found")
    })
    public ResponseEntity<FeedbackResponse> modifyFeedback(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Modify feedback payload", required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ModifyFeedbackRequest.class)
                    )
            )
            @RequestPart("request") @Valid ModifyFeedbackRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        log.info("CounselorController: Modify feedback request received");
        return ResponseEntity.ok(feedbackService.modifyFeedback(request, files));
    }

    /**
     * Marks a specific feedback as READ.
     *
     * @param feedbackId the UUID of the feedback to mark as read
     * @return ResponseEntity containing the updated FeedbackResponse.
     */
    @PatchMapping("/feedback/mark-read/{feedbackId}")
    @Operation(summary = "Mark feedback as read", description = "Marks a specific feedback sent to a student as READ")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Feedback successfully marked as read", content = @Content(mediaType = "application/json", schema = @Schema(implementation = FeedbackResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized or invalid token"),
            @ApiResponse(responseCode = "404", description = "Feedback not found")
    })
    public ResponseEntity<FeedbackResponse> markReadFeedback(@PathVariable UUID feedbackId) {
        log.info("CounselorController: Mark read feedback request received");
        return ResponseEntity.ok(feedbackService.markReadFeedback(feedbackId));
    }

    /**
     * Soft deletes a specific feedback by updating its status to DELETED.
     *
     * @param feedbackId the UUID of the feedback to delete
     * @return ResponseEntity with HttpStatus OK if successful.
     */
    @PatchMapping("/feedback/delete/{feedbackId}")
    @Operation(summary = "Soft delete feedback", description = "Marks a specific feedback sent to a student as DELETED")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Feedback successfully marked as deleted"),
            @ApiResponse(responseCode = "401", description = "Unauthorized or invalid token"),
            @ApiResponse(responseCode = "404", description = "Feedback not found")
    })
    public ResponseEntity<HttpStatus> deleteFeedback(@PathVariable UUID feedbackId) {
        log.info("CounselorController: Delete feedback request received for ID: {}", feedbackId);
        // Soft deletes the feedback by changing its status
        feedbackService.deleteFeedback(feedbackId);
        return ResponseEntity.ok(HttpStatus.OK);
    }

    @PostMapping("/export-students")
    @Operation(summary = "Export student list", description = "Export selected students to excel")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Selected student exported to excel successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized or invalid token"),
            @ApiResponse(responseCode = "404", description = "Student not found")
    })
    public ResponseEntity<byte[]> exportStudentList(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Selected students payload", required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExportStudentListRequest.class)
                    )
            )
            @RequestBody @Valid ExportStudentListRequest request
    ) {
        log.info("Export student list request received");
        byte[] excel = counselorService.exportStudentList(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=StudentList.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    @GetMapping("/curriculums")
    @Operation(summary = "Get Curriculums", description = "Get university curriculum codes")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Curriculums package", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CurriculumResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized or invalid token")
    })
    public ResponseEntity<CurriculumResponse> getCurriculums() {
        log.info("CounselorController: Get university curriculum codes request received");
        return ResponseEntity.ok(counselorService.getCurriculums());
    }

    @GetMapping("/import-student/{email}")
    @Operation(summary = "Validate email", description = "Validate student email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Student email is valid"),
            @ApiResponse(responseCode = "401", description = "Unauthorized or invalid token")
    })
    public ResponseEntity<HttpStatus> checkStudentEmail(@PathVariable String email) {
        log.info("CounselorController: Check student email request received");
        counselorService.checkStudentEmail(email);
        return ResponseEntity.ok(HttpStatus.OK);
    }

    @PostMapping("/import-students")
    @Operation(summary = "Import student accounts", description = "Import new student accounts"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Import student accounts successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized or invalid token")
    })
    public ResponseEntity<byte[]> importStudentAccounts(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Student accounts payload", required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ImportStudentAccountsRequest.class)
                    )
            )
            @RequestBody @Valid ImportStudentAccountsRequest request
    ) {
        log.info("CounselorController: Import student accounts request received");
        byte[] excel = counselorService.importStudentAccounts(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=StudentAccountList.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }
}
