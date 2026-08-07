package com.inteliroadmap.backend.domain.dto.response.portfolio;

import com.inteliroadmap.backend.domain.enums.EvidenceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PortfolioProjectResponse {
    private UUID projectId;
    private String projectName;
    private String repoUrl;
    private String demoUrl;
    private String description;
    private Map<String, Object> techStack;
    private String icon;
    private Integer stars;
}
