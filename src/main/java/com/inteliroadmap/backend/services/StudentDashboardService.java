package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.response.student.AiHistoryItemResponse;
import com.inteliroadmap.backend.domain.dto.response.student.DashboardRoadmapProgressResponse;
import com.inteliroadmap.backend.domain.dto.response.student.MarketDemandResponse;
import com.inteliroadmap.backend.domain.dto.response.student.MentorFeedbackItemResponse;
import com.inteliroadmap.backend.domain.dto.response.student.RecommendationItemResponse;
import com.inteliroadmap.backend.domain.dto.response.student.SkillGapItemResponse;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.enums.RoadmapStepStatus;
import com.inteliroadmap.backend.mappers.StudentDashboardMapper;
import com.inteliroadmap.backend.services.AuthenticatedStudentService;
import com.inteliroadmap.backend.services.StudentDashboardService;
import com.inteliroadmap.backend.services.StudentService;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;

public interface StudentDashboardService {

    public DashboardRoadmapProgressResponse getRoadmapProgress() ;

    DashboardRoadmapProgressResponse getRoadmapProgress(Student student);

    public List<SkillGapItemResponse> getSkillGaps() ;

    List<SkillGapItemResponse> getSkillGaps(Student student);

    public List<MentorFeedbackItemResponse> getMentorFeedback() ;

    void markFeedbackRead(java.util.UUID feedbackId);

    void dismissFeedback(java.util.UUID feedbackId);

    public List<AiHistoryItemResponse> getAiHistory() ;

    public MarketDemandResponse getMarketDemand() ;

    public List<RecommendationItemResponse> getRecommendations() ;
}
