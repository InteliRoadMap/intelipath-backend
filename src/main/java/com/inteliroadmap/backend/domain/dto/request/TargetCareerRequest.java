package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class TargetCareerRequest {
    @NotNull(message = "Career ID is required")
    private UUID careerId;
}
