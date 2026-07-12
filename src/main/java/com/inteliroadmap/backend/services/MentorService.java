package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.CreateFeedbackRequest;
import com.inteliroadmap.backend.domain.dto.request.UpdateMentorProfileRequest;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorCareerDistributionResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorDashboardMetricsResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorFeedbackHistoryResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorPendingReviewResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorProfileResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorProgressReportResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorStudentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface MentorService {

    MentorDashboardMetricsResponse getDashboardMetrics();

    MentorResponse getWelcomeAlert();

    MentorResponse getInsight();

    Page<MentorPendingReviewResponse> getPendingReviews(Pageable pageable);

    Page<MentorStudentResponse> getStudentInfos(Pageable pageable);

    List<MentorCareerDistributionResponse> getCareerDistribution();

    MentorProfileResponse getMentorProfile();

    MentorProfileResponse updateMentorProfile(UpdateMentorProfileRequest request);

    Page<MentorFeedbackHistoryResponse> getFeedbackHistory(Pageable pageable);

    MentorProgressReportResponse getProgressReports();

    MentorResponse submitFeedback(CreateFeedbackRequest request);
}
