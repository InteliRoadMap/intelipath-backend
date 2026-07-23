package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.response.student.AiHistoryItemResponse;
import com.inteliroadmap.backend.domain.dto.response.student.DashboardRoadmapProgressResponse;
import com.inteliroadmap.backend.domain.dto.response.student.MarketDemandResponse;
import com.inteliroadmap.backend.domain.dto.response.student.MentorFeedbackItemResponse;
import com.inteliroadmap.backend.domain.dto.response.student.RecommendationItemResponse;
import com.inteliroadmap.backend.domain.dto.response.student.SkillGapItemResponse;
import com.inteliroadmap.backend.domain.entity.Student;

import java.util.List;

public interface StudentDashboardService {

    DashboardRoadmapProgressResponse getRoadmapProgress();

    DashboardRoadmapProgressResponse getRoadmapProgress(Student student);

    List<SkillGapItemResponse> getSkillGaps();

    List<SkillGapItemResponse> getSkillGaps(Student student);

    List<MentorFeedbackItemResponse> getMentorFeedback();

    void markFeedbackRead(java.util.UUID feedbackId);

    void dismissFeedback(java.util.UUID feedbackId);

    List<AiHistoryItemResponse> getAiHistory();

    MarketDemandResponse getMarketDemand();

    List<RecommendationItemResponse> getRecommendations();
}
