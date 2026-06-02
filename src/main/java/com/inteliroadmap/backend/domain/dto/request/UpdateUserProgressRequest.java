package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserProgressRequest {

    @NotBlank(message = "Token is required")
    private String token;

    @NotBlank(message = "Node ID is required")
    private String nodeId;

    @NotBlank(message = "Node status is required")
    private String status;
}
