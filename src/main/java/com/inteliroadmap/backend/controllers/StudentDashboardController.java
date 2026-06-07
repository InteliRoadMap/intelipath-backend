package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.response.dashboard.*;
import com.inteliroadmap.backend.services.dashboard.StudentDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/student/dashboard")
@RequiredArgsConstructor
@Slf4j
public class StudentDashboardController {

    private final StudentDashboardService studentDashboardService;

    /**
     * Get roadmap progress for the authenticated student.
     *
     * @return roadmap progress response
     */
    @GetMapping("/roadmap-progress")
    public ResponseEntity<DashboardRoadmapProgressResponse> getRoadmapProgress() {
        log.info("Student Dashboard API: Get roadmap progress request received");
        return ResponseEntity.ok(studentDashboardService.getRoadmapProgress());
    }

    /**
     * Get skill gaps for the authenticated student.
     *
     * @return skill gap list
     */
    @GetMapping("/skill-gaps")
    public ResponseEntity<List<SkillGapItemResponse>> getSkillGaps() {
        log.info("Student Dashboard API: Get skill gaps request received");
        return ResponseEntity.ok(studentDashboardService.getSkillGaps());
    }

    /**
     * Get latest mentor feedback for the authenticated student.
     *
     * @return feedback list
     */
    @GetMapping("/mentor-feedback")
    public ResponseEntity<List<MentorFeedbackItemResponse>> getMentorFeedback() {
        log.info("Student Dashboard API: Get mentor feedback request received");
        return ResponseEntity.ok(studentDashboardService.getMentorFeedback());
    }

    /**
     * Get AI history for the authenticated student.
     *
     * @return AI history list
     */
    @GetMapping("/ai-history")
    public ResponseEntity<List<AiHistoryItemResponse>> getAiHistory() {
        log.info("Student Dashboard API: Get AI history request received");
        return ResponseEntity.ok(studentDashboardService.getAiHistory());
    }

    /**
     * Get market demand for the authenticated student's career.
     *
     * @return market demand response or null
     */
    @GetMapping("/market-demand")
    public ResponseEntity<MarketDemandResponse> getMarketDemand() {
        log.info("Student Dashboard API: Get market demand request received");
        return ResponseEntity.ok(studentDashboardService.getMarketDemand());
    }

    /**
     * Get recommendations generated from missing skills.
     *
     * @return recommendation list
     */
    @GetMapping("/recommendations")
    public ResponseEntity<List<RecommendationItemResponse>> getRecommendations() {
        log.info("Student Dashboard API: Get recommendations request received");
        return ResponseEntity.ok(studentDashboardService.getRecommendations());
    }
}
