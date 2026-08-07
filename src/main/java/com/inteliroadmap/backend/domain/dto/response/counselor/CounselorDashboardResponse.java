package com.inteliroadmap.backend.domain.dto.response.counselor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CounselorDashboardResponse {
    private int total;
    private Map<String, Integer> totalCareerStatistics;
    private Map<String, Integer> totalMissingSkills;
    private String careerName;
    private List<FeedbackResponse> feedbacks;
}
