package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.response.counselor.FeedbackResponse;

import com.inteliroadmap.backend.domain.dto.response.student.AiHistoryItemResponse;
import com.inteliroadmap.backend.domain.dto.response.student.DashboardRoadmapProgressResponse;
import com.inteliroadmap.backend.domain.dto.response.student.MarketDemandResponse;
import com.inteliroadmap.backend.domain.dto.response.student.MentorFeedbackItemResponse;
import com.inteliroadmap.backend.domain.dto.response.student.RecommendationItemResponse;
import com.inteliroadmap.backend.domain.dto.request.ReplyFeedbackRequest;
import com.inteliroadmap.backend.domain.dto.response.counselor.CounselorFeedbackResponse;
import com.inteliroadmap.backend.domain.dto.response.student.SkillGapItemResponse;
import com.inteliroadmap.backend.services.FeedbackService;
import jakarta.validation.Valid;
import com.inteliroadmap.backend.services.StudentDashboardService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/student/dashboard")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Student Dashboard", description = "Authenticated student dashboard endpoints")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasRole('STUDENT')")
public class StudentDashboardController {

    private final StudentDashboardService studentDashboardService;
    private final FeedbackService feedbackService;

    /**
     * Get roadmap progress for the authenticated student.
     *
     * @return roadmap progress response
     */
    @GetMapping("/roadmap-progress")
    @Operation(
            summary = "Get roadmap progress",
            description = "Get roadmap steps, current progress, and AI guidance for the authenticated student"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Roadmap progress fetched successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = DashboardRoadmapProgressResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized or invalid access token"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Student or roadmap not found"
            )
    })
    public ResponseEntity<DashboardRoadmapProgressResponse> getRoadmapProgress() {
        log.info("StudentDashboardController: Get roadmap progress request received");
        return ResponseEntity.ok(studentDashboardService.getRoadmapProgress());
    }

    /**
     * Get skill gaps for the authenticated student.
     *
     * @return skill gap list
     */
    @GetMapping("/skill-gaps")
    @Operation(
            summary = "Get skill gaps",
            description = "Get skills that the authenticated student is missing for the target career"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Skill gaps fetched successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SkillGapItemResponse.class)
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
    public ResponseEntity<List<SkillGapItemResponse>> getSkillGaps() {
        log.info("StudentDashboardController: Get skill gaps request received");
        return ResponseEntity.ok(studentDashboardService.getSkillGaps());
    }

    /**
     * Get latest mentor feedback for the authenticated student.
     *
     * @return feedback list
     */
    @GetMapping("/mentor-feedback")
    @Operation(
            summary = "Get mentor feedback",
            description = "Get the latest mentor feedback for the authenticated student"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Mentor feedback fetched successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MentorFeedbackItemResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized or invalid access token"
            )
    })
    public ResponseEntity<List<MentorFeedbackItemResponse>> getMentorFeedback() {
        log.info("StudentDashboardController: Get mentor feedback request received");
        return ResponseEntity.ok(studentDashboardService.getMentorFeedback());
    }

    /**
     * Mark one feedback item as read for the authenticated student.
     */
    @PatchMapping("/mentor-feedback/{feedbackId}/read")
    @Operation(summary = "Mark feedback as read", description = "Mark a mentor feedback item as read for the authenticated student")
    public ResponseEntity<Void> markFeedbackRead(@PathVariable java.util.UUID feedbackId) {
        log.info("StudentDashboardController: Mark feedback {} read", feedbackId);
        studentDashboardService.markFeedbackRead(feedbackId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Reply to a feedback message the authenticated student received.
     *
     * <p>The reply is stored as a feedback row in the opposite direction, so it
     * lands in the mentor's or counselor's own inbox with the same unread state
     * and notification as any other message.
     */
    @PostMapping("/mentor-feedback/{feedbackId}/reply")
    @Operation(summary = "Reply to feedback",
            description = "Sends the student's reply back to whoever wrote the feedback.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Reply sent"),
            @ApiResponse(responseCode = "400", description = "Empty reply"),
            @ApiResponse(responseCode = "401", description = "Unauthorized or invalid access token"),
            @ApiResponse(responseCode = "403", description = "The feedback was not addressed to this student"),
            @ApiResponse(responseCode = "404", description = "Feedback not found")
    })
    public ResponseEntity<FeedbackResponse> replyToFeedback(
            @PathVariable java.util.UUID feedbackId,
            @RequestBody @Valid ReplyFeedbackRequest request) {
        log.info("StudentDashboardController: Reply to feedback {}", feedbackId);
        return ResponseEntity.ok(feedbackService.replyToFeedback(feedbackId, request.getContent()));
    }

    /**
     * Dismiss (soft-hide) one feedback item for the authenticated student.
     */
    @DeleteMapping("/mentor-feedback/{feedbackId}")
    @Operation(summary = "Dismiss feedback", description = "Dismiss (soft-hide) a mentor feedback item for the authenticated student")
    public ResponseEntity<Void> dismissFeedback(@PathVariable java.util.UUID feedbackId) {
        log.info("StudentDashboardController: Dismiss feedback {}", feedbackId);
        studentDashboardService.dismissFeedback(feedbackId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get AI history for the authenticated student.
     *
     * @return AI history list
     */
    @GetMapping("/ai-history")
    @Operation(
            summary = "Get AI history",
            description = "Get AI interaction history for the authenticated student"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "AI history fetched successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AiHistoryItemResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized or invalid access token"
            )
    })
    public ResponseEntity<List<AiHistoryItemResponse>> getAiHistory() {
        log.info("StudentDashboardController: Get AI history request received");
        return ResponseEntity.ok(studentDashboardService.getAiHistory());
    }

    /**
     * Get market demand for the authenticated student's career.
     *
     * @return market demand response or null
     */
    @GetMapping("/market-demand")
    @Operation(
            summary = "Get market demand",
            description = "Get market demand information for the authenticated student's target career"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Market demand fetched successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MarketDemandResponse.class)
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
    public ResponseEntity<MarketDemandResponse> getMarketDemand() {
        log.info("StudentDashboardController: Get market demand request received");
        return ResponseEntity.ok(studentDashboardService.getMarketDemand());
    }

    /**
     * Get recommendations generated from missing skills.
     *
     * @return recommendation list
     */
    @GetMapping("/recommendations")
    @Operation(
            summary = "Get recommendations",
            description = "Get learning recommendations generated from the authenticated student's missing skills"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Recommendations fetched successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RecommendationItemResponse.class)
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
    public ResponseEntity<List<RecommendationItemResponse>> getRecommendations() {
        log.info("StudentDashboardController: Get recommendations request received");
        return ResponseEntity.ok(studentDashboardService.getRecommendations());
    }
}
