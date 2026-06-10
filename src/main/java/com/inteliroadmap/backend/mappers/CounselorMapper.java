package com.inteliroadmap.backend.mappers;

import com.inteliroadmap.backend.domain.dto.response.CounselorResponse;
import com.inteliroadmap.backend.domain.dto.response.UpdateProfileResponse;
import com.inteliroadmap.backend.domain.entity.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class CounselorMapper {
    public CounselorResponse toRoadmapStatisticResponse(int total, Map<String, Integer> careerStatistics) {
        return CounselorResponse.builder()
                .total(total)
                .careerStatistics(careerStatistics)
                .build();
    }

    public CounselorResponse toMissingSkillsResponse(int total, Map<String, Integer> missingSkills, String careerName) {
        return CounselorResponse.builder()
                .total(total)
                .missingSkills(missingSkills)
                .careerName(careerName)
                .build();
    }

    public CounselorResponse getStudentInfos(List<Map<String, Object>> stInfos) {
        return CounselorResponse.builder()
                .students(stInfos)
                .build();
    }

    public CounselorResponse toGetFeedbacksResponse(List<Feedback> feedbacks, int total) {
        return CounselorResponse.builder()
                .feedbacks(feedbacks)
                .total(total)
                .build();
    }

    public CounselorResponse toCrudFeedbackResponse(Feedback feedback) {
        return CounselorResponse.builder()
                .feedback(feedback)
                .build();
    }

    public UpdateProfileResponse toCrudProfileResponse(User user, AcademicCounselor academicCounselor) {
        return UpdateProfileResponse.builder()
                .user(user)
                .academicCounselor(academicCounselor)
                .build();
    }

}
