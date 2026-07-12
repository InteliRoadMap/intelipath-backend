package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SetupUserProfileRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    private String yob;

    private String bio;
}
