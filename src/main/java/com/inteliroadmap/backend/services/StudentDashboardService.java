package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.response.student.*;
import com.inteliroadmap.backend.domain.entity.*;
import com.inteliroadmap.backend.domain.enums.RoadmapStepStatus;
import com.inteliroadmap.backend.mappers.StudentDashboardMapper;
import com.inteliroadmap.backend.repositories.*;
import com.inteliroadmap.backend.services.AuthenticatedStudentService;
import com.inteliroadmap.backend.services.StudentDashboardService;
import com.inteliroadmap.backend.services.StudentService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.*;

public interface StudentDashboardService {

    public DashboardRoadmapProgressResponse getRoadmapProgress() ;

    DashboardRoadmapProgressResponse getRoadmapProgress(Student student);

    public List<SkillGapItemResponse> getSkillGaps() ;

    List<SkillGapItemResponse> getSkillGaps(Student student);

    public List<MentorFeedbackItemResponse> getMentorFeedback() ;

    public List<AiHistoryItemResponse> getAiHistory() ;

    public MarketDemandResponse getMarketDemand() ;

    public List<RecommendationItemResponse> getRecommendations() ;
}
