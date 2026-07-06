package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.CreateFeedbackRequest;
import com.inteliroadmap.backend.domain.dto.request.ModifyFeedbackRequest;
import com.inteliroadmap.backend.domain.dto.response.FeedbackResponse;

import java.util.UUID;

public interface FeedbackService {

    FeedbackResponse createFeedback(CreateFeedbackRequest request) ;

    FeedbackResponse modifyFeedback(ModifyFeedbackRequest request) ;

    FeedbackResponse markReadFeedback(UUID feedbackId) ;

    void deleteFeedback(UUID feedbackId) ;
}
