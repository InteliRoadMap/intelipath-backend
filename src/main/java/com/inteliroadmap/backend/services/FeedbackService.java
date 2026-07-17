package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.CreateFeedbackRequest;
import com.inteliroadmap.backend.domain.dto.request.ModifyFeedbackRequest;
import com.inteliroadmap.backend.domain.dto.response.counselor.FeedbackResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface FeedbackService {

    FeedbackResponse createFeedback(CreateFeedbackRequest request, List<MultipartFile> files);

    FeedbackResponse modifyFeedback(ModifyFeedbackRequest request, List<MultipartFile> files);

    FeedbackResponse markReadFeedback(UUID feedbackId);

    void deleteFeedback(UUID feedbackId);
}
