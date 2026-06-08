package com.inteliroadmap.backend.domain.dto.response;

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
    private Map<String, Integer> careerStatistics;
    private Map<String, Integer> missingSkills;
    private List<Feedback>  feedbacks;
    private Feedback feedback;
//    private Map<String, String> studentInfo;
//    private List<Map<String, String>> feedbackInfo;
//    private List<String> missingSkills;
//    private Double roadmapProgress;
}
