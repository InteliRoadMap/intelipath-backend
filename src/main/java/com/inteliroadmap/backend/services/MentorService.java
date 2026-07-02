package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.CreateFeedbackRequest;
import com.inteliroadmap.backend.domain.dto.request.UpdateMentorProfileRequest;
import com.inteliroadmap.backend.domain.dto.response.MentorResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorCareerDistributionResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorDashboardMetrics;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorFeedbackHistoryDto;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorPendingReviewResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorProfileResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorProgressReportDto;
import com.inteliroadmap.backend.domain.dto.response.mentor.MentorStudentDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface MentorService {

    MentorDashboardMetrics getDashboardMetrics();

    Page<MentorPendingReviewResponse> getPendingReviews(Pageable pageable);

    Page<MentorStudentDto> getStudentInfos(Pageable pageable);

    List<MentorCareerDistributionResponse> getCareerDistribution();

    MentorProfileResponse getMentorProfile();

    MentorProfileResponse updateMentorProfile(UpdateMentorProfileRequest request);

    Page<MentorFeedbackHistoryDto> getFeedbackHistory(Pageable pageable);

    MentorProgressReportDto getProgressReports();

    MentorResponse submitFeedback(CreateFeedbackRequest request);
}
