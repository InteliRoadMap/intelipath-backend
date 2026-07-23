package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SetupUserProfileRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 150, message = "Full name must not exceed 150 characters")
    private String fullName;

    @Size(max = 10, message = "Year of birth must not exceed 10 characters")
    private String yob;

    @Size(max = 2000, message = "Bio must not exceed 2000 characters")
    private String bio;
}
