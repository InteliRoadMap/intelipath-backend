package com.inteliroadmap.backend.domain.dto.response;

import com.inteliroadmap.backend.domain.enums.FeedbackType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackResponse {
    private UUID feedbackId;
    private UUID senderId;
    private UUID receiverId;
    private String senderName;
    private String content;
    private FeedbackType type;
    private Integer rating;
    private com.inteliroadmap.backend.domain.enums.FeedbackStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
