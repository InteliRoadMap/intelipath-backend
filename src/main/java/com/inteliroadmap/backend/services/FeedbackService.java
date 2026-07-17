package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.CreateFeedbackRequest;
import com.inteliroadmap.backend.domain.dto.request.ModifyFeedbackRequest;
import com.inteliroadmap.backend.domain.dto.response.counselor.CounselorFeedbackResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface FeedbackService {

    CounselorFeedbackResponse.FeedbackResponse createFeedback(CreateFeedbackRequest request, List<MultipartFile> files);

    CounselorFeedbackResponse.FeedbackResponse modifyFeedback(ModifyFeedbackRequest request, List<MultipartFile> files);

    CounselorFeedbackResponse.FeedbackResponse markReadFeedback(UUID feedbackId);

    void deleteFeedback(UUID feedbackId);
}
