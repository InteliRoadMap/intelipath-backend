package com.inteliroadmap.backend.mappers;

import com.inteliroadmap.backend.domain.dto.response.counselor.FeedbackResponse;

import com.inteliroadmap.backend.domain.dto.response.counselor.CounselorDashboardResponse;
import com.inteliroadmap.backend.domain.dto.response.counselor.CurriculumResponse;
import com.inteliroadmap.backend.domain.dto.response.counselor.CounselorFeedbackResponse;
import com.inteliroadmap.backend.domain.dto.response.student.UpdateProfileResponse;
import com.inteliroadmap.backend.domain.dto.response.FeedbackAttachmentResponse;
import com.inteliroadmap.backend.domain.entity.AcademicCounselor;
import com.inteliroadmap.backend.domain.entity.Feedback;
import com.inteliroadmap.backend.domain.entity.User;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CounselorMapper {
    public CounselorDashboardResponse toRoadmapStatisticResponse(int total, Map<String, Integer> careerStatistics) {
        return CounselorDashboardResponse.builder()
                .total(total)
                .totalCareerStatistics(careerStatistics)
                .build();
    }

    public CounselorDashboardResponse toMissingSkillsResponse(int total, Map<String, Integer> missingSkills, String careerName) {
        return CounselorDashboardResponse.builder()
                .total(total)
                .totalMissingSkills(missingSkills)
                .careerName(careerName)
                .build();
    }

    public CounselorFeedbackResponse toGetStudentInfos(List<Map<String, Object>> stInfos, List<String> careers, int totalPages, int currentPage) {
        return CounselorFeedbackResponse.builder()
                .students(stInfos)
                .careers(careers)
                .totalPages(totalPages)
                .currentPage(currentPage)
                .build();
    }

    public CounselorDashboardResponse toGetFeedbacksResponse(List<Feedback> feedbacks, int total) {
        List<FeedbackResponse> dtos = feedbacks.stream()
                .map(this::mapFeedbackToResponse)
                .toList();
        return CounselorDashboardResponse.builder()
                .feedbacks(dtos)
                .total(total)
                .build();
    }

    public CounselorFeedbackResponse toGetStudentStatisticAndFeedback(int progress, List<String> missingSkills, List<Feedback> feedbacks) {
        List<FeedbackResponse> dtos = feedbacks.stream()
                .map(this::mapFeedbackToResponse)
                .toList();
        return CounselorFeedbackResponse.builder()
                .roadmapProgress(progress)
                .missingSkills(missingSkills)
                .feedbacks(dtos)
                .build();
    }

    private FeedbackResponse mapFeedbackToResponse(Feedback f) {
        return FeedbackResponse.builder()
                .feedbackId(f.getFeedbackId())
                .senderId(f.getSender().getUserId())
                .receiverId(f.getReceiver().getUserId())
                .senderName(f.getSenderName())
                .receiverName(f.getReceiver().getFullName())
                .content(f.getContent())
                .type(f.getType())
                .status(f.getStatus())
                .attachments(
                        f.getAttachments() != null ? f.getAttachments().stream()
                                .map(att -> FeedbackAttachmentResponse.builder()
                                        .attachmentId(att.getAttachmentId())
                                        .fileName(att.getFileName())
                                        .fileType(att.getFileType())
                                        .fileSize(att.getFileSize())
                                        .build())
                                .collect(Collectors.toList()) : new ArrayList<>()
                )
                .createdAt(f.getCreatedAt())
                .updatedAt(f.getUpdatedAt())
                .build();
    }

    public UpdateProfileResponse toCrudProfileResponse(User user, AcademicCounselor academicCounselor) {
        return UpdateProfileResponse.builder()
                .user(user)
                .academicCounselor(academicCounselor)
                .build();
    }

    public CurriculumResponse toCurriculumResponse(List<String> curriculums) {
        return CurriculumResponse.builder()
                .curriculums(curriculums)
                .build();
    }
}
