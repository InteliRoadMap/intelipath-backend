package com.inteliroadmap.backend.domain.dto.response.portfolio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Read-only learning progress exposed on a student's portfolio. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningJourneyResponse {
    private String targetCareerRole;
    private Integer progress;
    private Double readiness;
    private Double readinessVerified;
    private Integer readinessRequiredCount;
    private Integer readinessHeldCount;
    private Integer readinessVerifiedCount;
    private List<CoreSkillSummaryResponse> coreSkills;
    private List<RoadmapStageResponse> stages;
}
