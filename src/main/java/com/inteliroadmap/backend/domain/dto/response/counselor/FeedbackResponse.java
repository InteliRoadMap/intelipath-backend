package com.inteliroadmap.backend.domain.dto.response.counselor;

import com.inteliroadmap.backend.domain.dto.response.FeedbackAttachmentResponse;
import com.inteliroadmap.backend.domain.enums.FeedbackStatus;
import com.inteliroadmap.backend.domain.enums.FeedbackType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class FeedbackResponse {
    private UUID feedbackId;
    private UUID senderId;
    private UUID receiverId;
    private String senderName;
    private String receiverName;
    private String content;
    private FeedbackType type;
    private FeedbackStatus status;
    private List<FeedbackAttachmentResponse> attachments;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
