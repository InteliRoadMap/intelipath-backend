package com.inteliroadmap.backend.domain.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioUpsertRequest {
    private PortfolioConfigRequest config;
    private List<PortfolioProjectRequest> projects;
    private List<StudentEducationRequest> education;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PortfolioConfigRequest {
        private String theme;
        private Map<String, Object> themeColors;
        private Map<String, Object> fonts;
        private Map<String, Object> heroSection;
        private Map<String, Object> skillsSection;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PortfolioProjectRequest {
        private UUID projectId;
        private String projectName;
        private String repoUrl;
        private String demoUrl;
        private String description;
        private Map<String, Object> techStack;
        private String icon;
        private Integer stars;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StudentEducationRequest {
        private UUID educationId;
        private String university;
        private String degree;
        private String period;
        private String description;
    }
}
