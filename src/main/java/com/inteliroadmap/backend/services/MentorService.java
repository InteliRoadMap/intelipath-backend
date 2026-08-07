package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.CreateFeedbackRequest;
import com.inteliroadmap.backend.domain.dto.request.UpdateMentorProfileRequest;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorCareerDistributionResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorDashboardMetricsResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorDirectoryResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorFeedbackHistoryResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorPendingReviewResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorProfileResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorProgressReportResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorStudentResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.GithubImportAuditResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface MentorService {

    MentorDashboardMetricsResponse getDashboardMetrics();

    MentorResponse getWelcomeAlert();

    MentorResponse getInsight();

    Page<MentorPendingReviewResponse> getPendingReviews(Pageable pageable);

    Page<MentorStudentResponse> getStudentInfos(Pageable pageable);

    /**
     * The mentor directory a student browses before asking for a portfolio review.
     * Student-facing, so unlike every other method here it is not scoped to the
     * caller's own mentor account.
     */
    Page<MentorDirectoryResponse> getMentorDirectory(Pageable pageable);

    List<MentorCareerDistributionResponse> getCareerDistribution();

    MentorProfileResponse getMentorProfile();

    MentorProfileResponse updateMentorProfile(UpdateMentorProfileRequest request);

    Page<MentorFeedbackHistoryResponse> getFeedbackHistory(Pageable pageable);

    MentorProgressReportResponse getProgressReports();

    MentorResponse submitFeedback(CreateFeedbackRequest request);

    /**
     * Read-only view of an existing AI repository analysis for a student who asked this
     * mentor to review their portfolio.  It never re-fetches the repository or exposes
     * the student's GitHub credentials.
     */
    GithubImportAuditResponse getStudentImportAudit(UUID studentId, String repoUrl);
}
