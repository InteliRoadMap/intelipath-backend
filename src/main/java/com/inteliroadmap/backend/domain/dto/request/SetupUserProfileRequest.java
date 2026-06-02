package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class SetupUserProfileRequest {

    @NotBlank(message = "Token is required")
    private String token;

    @NotBlank(message = "Full name is required")
    private String fullName;

    private String yob;

    private String bio;
}
