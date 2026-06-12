package com.inteliroadmap.backend.domain.dto.request;

import com.inteliroadmap.backend.domain.enums.FeedbackType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateFeedbackRequest {
    @NotNull(message = "Receiver ID is required")
    private UUID receiverId;

    @NotBlank(message = "Content is required")
    private String content;

    @NotBlank(message = "Type is required")
    private FeedbackType type;
}
