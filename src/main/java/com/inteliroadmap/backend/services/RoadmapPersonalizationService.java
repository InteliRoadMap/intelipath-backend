package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapRecommendationDecisionResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapRecommendationResponse;

import java.util.List;
import java.util.UUID;

/**
 * Personalizes a student's roadmap from skill evidence.
 *
 * The AI (or the student's own profile skills) only ever produces
 * recommendations; nothing touches student_progress or student_skills until
 * the student explicitly accepts a recommendation.
 */
public interface RoadmapPersonalizationService {

    /**
     * Analyzes the current student's profile skills and skill evidence, then
     * creates a PENDING recommendation for roadmap nodes they can likely skip.
     * Returns an empty list when there is nothing new to recommend.
     */
    List<RoadmapRecommendationResponse> generateRecommendationsForCurrentStudent();

    /** Lists the current student's PENDING recommendations, newest first. */
    List<RoadmapRecommendationResponse> getPendingRecommendationsForCurrentStudent();

    /**
     * Applies a recommendation: marks its MARK_COMPLETE nodes as completed,
     * syncs fully-completed skills into student_skills, links the supporting
     * evidence, and returns the recalculated roadmap progress.
     */
    RoadmapRecommendationDecisionResponse acceptRecommendation(UUID recommendationId);

    /** Declines a recommendation without touching progress or skills. */
    RoadmapRecommendationDecisionResponse rejectRecommendation(UUID recommendationId);

    /** Recomputes parent topics from already completed descendants. */
    void reconcileCompletedTopicsForCurrentStudent();
}
