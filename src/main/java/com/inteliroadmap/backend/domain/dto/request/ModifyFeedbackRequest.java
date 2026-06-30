package com.inteliroadmap.backend.domain.dto.request;

import com.inteliroadmap.backend.domain.enums.FeedbackType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ModifyFeedbackRequest {
    @NotNull(message = "Feedback ID is required")
    private UUID feedbackId;

    @NotBlank(message = "Content is required")
    private String content;

    @NotNull(message = "Type is required")
    private FeedbackType type;

    private Integer rating;
}
