package com.inteliroadmap.backend.domain.dto.request;

import com.inteliroadmap.backend.domain.enums.FeedbackType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class ModifyFeedbackRequest {
    @NotNull(message = "Feedback ID is required")
    private UUID feedbackId;

    @NotBlank(message = "Content is required")
    @Size(max = 10000, message = "Content must not exceed 10000 characters")
    private String content;

    @NotNull(message = "Type is required")
    private FeedbackType type;
}
