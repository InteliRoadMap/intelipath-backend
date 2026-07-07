package com.inteliroadmap.backend.domain.dto.response.counselor;

import com.inteliroadmap.backend.domain.entity.Feedback;
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
public class CounselorResponse {
    private int total;
    private int totalPages;
    private int currentPage;
    private Map<String, Integer> totalCareerStatistics;
    private Map<String, Integer> totalMissingSkills;
    private String careerName;
    private List<FeedbackResponse> feedbacks;
    private List<Map<String, Object>> students;
    private Map<String, Object> studentInfo;
    private List<String> missingSkills;
    private int roadmapProgress;
}
