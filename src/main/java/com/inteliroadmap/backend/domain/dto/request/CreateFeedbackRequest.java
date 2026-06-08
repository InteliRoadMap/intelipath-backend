package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateFeedbackRequest {
    @NotBlank(message = "Receiver ID is required")
    private UUID receiverId;

    @NotBlank(message = "Content is required")
    private String content;

    @NotBlank(message = "Type is required")
    private String type;
}
