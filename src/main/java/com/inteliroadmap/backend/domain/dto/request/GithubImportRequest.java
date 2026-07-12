package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GithubImportRequest {
    @NotBlank(message = "GitHub Repository URL cannot be blank")
    @Size(max = 2048, message = "Repository URL must not exceed 2048 characters")
    @Pattern(regexp = "^https?://(www\\.)?github\\.com/.+",
            message = "Must be a valid GitHub repository URL")
    private String repoUrl;
}
