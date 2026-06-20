package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GithubImportRequest {
    @NotBlank(message = "GitHub Repository URL cannot be blank")
    private String repoUrl;
}
