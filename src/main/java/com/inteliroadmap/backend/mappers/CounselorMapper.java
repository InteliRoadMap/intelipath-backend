package com.inteliroadmap.backend.mappers;

import com.inteliroadmap.backend.domain.dto.response.counselor.CounselorResponse;
import com.inteliroadmap.backend.domain.dto.response.counselor.FeedbackResponse;
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
    public CounselorResponse toRoadmapStatisticResponse(int total, Map<String, Integer> careerStatistics) {
        return CounselorResponse.builder()
                .total(total)
                .totalCareerStatistics(careerStatistics)
                .build();
    }

    public CounselorResponse toMissingSkillsResponse(int total, Map<String, Integer> missingSkills, String careerName) {
        return CounselorResponse.builder()
                .total(total)
                .totalMissingSkills(missingSkills)
                .careerName(careerName)
                .build();
    }

    public CounselorResponse toGetStudentInfos(List<Map<String, Object>> stInfos, int totalPages, int currentPage) {
        return CounselorResponse.builder()
                .students(stInfos)
                .totalPages(totalPages)
                .currentPage(currentPage)
                .build();
    }

    public CounselorResponse toGetFeedbacksResponse(List<Feedback> feedbacks, int total) {
        List<FeedbackResponse> dtos = feedbacks.stream()
                .map(this::mapFeedbackToResponse)
                .toList();
        return CounselorResponse.builder()
                .feedbacks(dtos)
                .total(total)
                .build();
    }

    public CounselorResponse toGetStudentStatisticAndFeedback(int progress, List<String> missingSkills, List<Feedback> feedbacks) {
        List<FeedbackResponse> dtos = feedbacks.stream()
                .map(this::mapFeedbackToResponse)
                .toList();
        return CounselorResponse.builder()
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

}
