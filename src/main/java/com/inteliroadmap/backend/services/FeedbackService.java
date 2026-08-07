package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.response.counselor.FeedbackResponse;

import com.inteliroadmap.backend.domain.dto.request.CreateFeedbackRequest;
import com.inteliroadmap.backend.domain.dto.request.ModifyFeedbackRequest;
import com.inteliroadmap.backend.domain.dto.response.counselor.CounselorFeedbackResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface FeedbackService {

    FeedbackResponse createFeedback(CreateFeedbackRequest request, List<MultipartFile> files);

    FeedbackResponse modifyFeedback(ModifyFeedbackRequest request, List<MultipartFile> files);

    FeedbackResponse markReadFeedback(UUID feedbackId);

    void deleteFeedback(UUID feedbackId);

    /**
     * Replies to a feedback message the authenticated user received.
     *
     * <p>A reply is an ordinary feedback row sent the other way, so the existing
     * inbox, read state and attachments all keep working without a thread table.
     * Only the recipient of a message may reply to it — otherwise any student
     * could post into another student's conversation by guessing an id.
     *
     * @param feedbackId the message being replied to
     * @param content    the reply body
     */
    FeedbackResponse replyToFeedback(UUID feedbackId, String content);
}
