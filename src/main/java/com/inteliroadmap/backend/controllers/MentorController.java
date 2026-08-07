package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.request.CreateFeedbackRequest;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorCareerDistributionResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorDashboardMetricsResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorFeedbackHistoryResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorPendingReviewResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorProfileResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorProgressReportResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorStudentResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.GithubImportAuditResponse;
import com.inteliroadmap.backend.domain.dto.request.UpdateMentorProfileRequest;
import com.inteliroadmap.backend.services.MentorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mentor")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Mentor Dashboard", description = "Industry Mentor APIs")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasRole('MENTOR')")
public class MentorController {

    private final MentorService mentorService;

    @GetMapping("/dashboard/metrics")
    @Operation(summary = "Get Mentor Metrics", description = "Get response time, mentees, pending reviews, etc.")
    public ResponseEntity<MentorDashboardMetricsResponse> getMetrics() {
        log.info("MentorController: Mentor metrics request received");
        return ResponseEntity.ok(mentorService.getDashboardMetrics());
    }

    @GetMapping("/dashboard/welcome-alert")
    @Operation(summary = "Get Welcome Alert", description = "Get dynamic welcome alert for mentor")
    public ResponseEntity<MentorResponse> getWelcomeAlert() {
        return ResponseEntity.ok(mentorService.getWelcomeAlert());
    }

    @GetMapping("/dashboard/insight")
    @Operation(summary = "Get Mentor Insight", description = "Get dynamic insight based on pending reviews")
    public ResponseEntity<MentorResponse> getInsight() {
        return ResponseEntity.ok(mentorService.getInsight());
    }

    @GetMapping("/dashboard/pending-reviews")
    @Operation(summary = "Get Pending Reviews", description = "Get list of pending review requests with pagination")
    public ResponseEntity<Page<MentorPendingReviewResponse>> getPendingReviews(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page index must not be negative") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "Page size must be at least 1") @Max(value = 100, message = "Page size must not exceed 100") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(mentorService.getPendingReviews(pageable));
    }

    @GetMapping("/dashboard/career-distribution")
    @Operation(summary = "Get Career Distribution", description = "Get data for career distribution chart of mentees")
    public ResponseEntity<List<MentorCareerDistributionResponse>> getCareerDistribution() {
        return ResponseEntity.ok(mentorService.getCareerDistribution());
    }

    @GetMapping("/feedback/students")
    @Operation(summary = "Get Student List", description = "Get list of students with pagination")
    public ResponseEntity<Page<MentorStudentResponse>> getStudentInfos(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page index must not be negative") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "Page size must be at least 1") @Max(value = 100, message = "Page size must not exceed 100") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(mentorService.getStudentInfos(pageable));
    }

    @GetMapping("/portfolio/audit")
    @Operation(summary = "Read a student's saved GitHub analysis",
            description = "Returns the stored AI-analysis snapshot only for a student who requested a portfolio review from the authenticated mentor. It never exposes GitHub source code or credentials.")
    public ResponseEntity<GithubImportAuditResponse> getStudentImportAudit(
            @RequestParam UUID studentId,
            @RequestParam String repoUrl) {
        GithubImportAuditResponse audit = mentorService.getStudentImportAudit(studentId, repoUrl);
        return audit == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(audit);
    }

    @GetMapping("/feedback/history")
    @Operation(summary = "Get Feedback History", description = "Get list of feedbacks sent by mentor")
    public ResponseEntity<Page<MentorFeedbackHistoryResponse>> getFeedbackHistory(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page index must not be negative") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "Page size must be at least 1") @Max(value = 100, message = "Page size must not exceed 100") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(mentorService.getFeedbackHistory(pageable));
    }

    @GetMapping("/dashboard/progress-reports")
    @Operation(summary = "Get Progress Reports", description = "Get detailed progress reports of mentees")
    public ResponseEntity<MentorProgressReportResponse> getProgressReports() {
        return ResponseEntity.ok(mentorService.getProgressReports());
    }

    @PostMapping("/feedback/submit")
    @Operation(summary = "Submit Feedback", description = "Mentor submits feedback for a student's portfolio")
    public ResponseEntity<MentorResponse> submitFeedback(@RequestBody @Valid CreateFeedbackRequest request) {
        log.info("MentorController: Mentor submit feedback request received");
        return ResponseEntity.ok(mentorService.submitFeedback(request));
    }

    @GetMapping("/profile")
    @Operation(summary = "Get Mentor Profile", description = "Get profile information of the authenticated mentor")
    public ResponseEntity<MentorProfileResponse> getMentorProfile() {
        return ResponseEntity.ok(mentorService.getMentorProfile());
    }

    @PatchMapping("/profile")
    @Operation(summary = "Update Mentor Profile", description = "Update professional information (company, industry focus) of the authenticated mentor")
    public ResponseEntity<MentorProfileResponse> updateMentorProfile(@RequestBody @Valid UpdateMentorProfileRequest request) {
        return ResponseEntity.ok(mentorService.updateMentorProfile(request));
    }
}
