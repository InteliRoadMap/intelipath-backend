package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.request.CreateFeedbackRequest;
import com.inteliroadmap.backend.domain.dto.response.MentorResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.*;
import com.inteliroadmap.backend.services.MentorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/mentor")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Mentor Dashboard", description = "Industry Mentor APIs")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasRole('MENTOR')")
public class MentorController {

    private final MentorService mentorService;

    @GetMapping("/dashboard/metrics")
    @Operation(summary = "Get Mentor Metrics", description = "Get rating, response time, mentees, pending reviews, etc.")
    public ResponseEntity<MentorDashboardMetrics> getMetrics() {
        log.info("Mentor metrics request received");
        return ResponseEntity.ok(mentorService.getDashboardMetrics());
    }

    @GetMapping("/dashboard/welcome-alert")
    @Operation(summary = "Get Welcome Alert", description = "Get dynamic welcome alert for mentor")
    public ResponseEntity<MentorResponse> getWelcomeAlert() {
        return ResponseEntity.ok(MentorResponse.builder().welcomeAlert("Chào ngày mới! Bạn có một số Portfolio đang chờ duyệt.").build());
    }

    @GetMapping("/dashboard/insight")
    @Operation(summary = "Get Mentor Insight", description = "Get AI-driven insight")
    public ResponseEntity<MentorResponse> getInsight() {
        return ResponseEntity.ok(MentorResponse.builder().insight("Có 3 sinh viên đang chờ feedback Portfolio từ bạn.").build());
    }

    @GetMapping("/dashboard/pending-reviews")
    @Operation(summary = "Get Pending Reviews", description = "Get list of pending review requests with pagination")
    public ResponseEntity<Page<MentorPendingReviewResponse>> getPendingReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(mentorService.getPendingReviews(pageable));
    }

    @GetMapping("/dashboard/career-distribution")
    @Operation(summary = "Get Career Distribution", description = "Get data for career distribution chart of mentees")
    public ResponseEntity<java.util.List<MentorCareerDistributionResponse>> getCareerDistribution() {
        return ResponseEntity.ok(mentorService.getCareerDistribution());
    }

    @GetMapping("/feedback/students")
    @Operation(summary = "Get Student List", description = "Get list of students with pagination")
    public ResponseEntity<Page<MentorStudentDto>> getStudentInfos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(mentorService.getStudentInfos(pageable));
    }

    @GetMapping("/feedback/history")
    @Operation(summary = "Get Feedback History", description = "Get list of feedbacks sent by mentor")
    public ResponseEntity<Page<MentorFeedbackHistoryDto>> getFeedbackHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(mentorService.getFeedbackHistory(pageable));
    }

    @GetMapping("/dashboard/progress-reports")
    @Operation(summary = "Get Progress Reports", description = "Get detailed progress reports of mentees")
    public ResponseEntity<MentorProgressReportDto> getProgressReports() {
        return ResponseEntity.ok(mentorService.getProgressReports());
    }

    @PostMapping("/feedback/submit")
    @Operation(summary = "Submit Feedback", description = "Mentor submits feedback for a student's portfolio")
    public ResponseEntity<MentorResponse> submitFeedback(@RequestBody @Valid CreateFeedbackRequest request) {
        log.info("Mentor submit feedback request received");
        return ResponseEntity.ok(mentorService.submitFeedback(request));
    }

    @GetMapping("/profile")
    @Operation(summary = "Get Mentor Profile", description = "Get profile information of the authenticated mentor")
    public ResponseEntity<MentorProfileResponse> getMentorProfile() {
        return ResponseEntity.ok(mentorService.getMentorProfile());
    }

    @PatchMapping("/profile")
    @Operation(summary = "Update Mentor Profile", description = "Update professional information (company, industry focus) of the authenticated mentor")
    public ResponseEntity<MentorProfileResponse> updateMentorProfile(@RequestBody @Valid com.inteliroadmap.backend.domain.dto.request.UpdateMentorProfileRequest request) {
        return ResponseEntity.ok(mentorService.updateMentorProfile(request));
    }
}
