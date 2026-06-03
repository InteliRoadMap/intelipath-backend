package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
public class UpdateUserProgressRequest {

    @NotNull(message = "Node ID is required")
    private UUID nodeId;

    @NotBlank(message = "Node status is required")
    private String status;
}
