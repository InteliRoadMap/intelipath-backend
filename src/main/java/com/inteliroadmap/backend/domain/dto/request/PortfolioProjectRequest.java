package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PortfolioProjectRequest {
    private UUID projectId;

    @Size(max = 200, message = "Project name must not exceed 200 characters")
    private String projectName;

    @Size(max = 2048, message = "Repo URL must not exceed 2048 characters")
    private String repoUrl;

    @Size(max = 2048, message = "Demo URL must not exceed 2048 characters")
    private String demoUrl;

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    private String description;

    private Map<String, Object> techStack;

    @Size(max = 100, message = "Icon must not exceed 100 characters")
    private String icon;

    @PositiveOrZero(message = "Stars must be zero or positive")
    private Integer stars;
}
