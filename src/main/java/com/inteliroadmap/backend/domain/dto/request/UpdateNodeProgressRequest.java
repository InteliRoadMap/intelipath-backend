package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateNodeProgressRequest {

    @NotNull(message = "Node ID is required")
    private UUID nodeId;

    @NotBlank(message = "Status is required")
    @Pattern(regexp = "(?i)^(NOT_STARTED|IN_PROGRESS|COMPLETED|LOCKED)$",
            message = "Status must be one of NOT_STARTED, IN_PROGRESS, COMPLETED, LOCKED")
    private String status;
}
