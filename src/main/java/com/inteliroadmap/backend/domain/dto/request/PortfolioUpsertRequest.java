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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioUpsertRequest {
    @Valid
    private PortfolioConfigRequest config;

    @Valid
    private List<PortfolioProjectRequest> projects;

    @Valid
    private List<StudentEducationRequest> education;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PortfolioConfigRequest {
        @Size(max = 50, message = "Theme must not exceed 50 characters")
        private String theme;
        private Map<String, Object> themeColors;
        private Map<String, Object> fonts;
        private Map<String, Object> heroSection;
        private Map<String, Object> skillsSection;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PortfolioProjectRequest {
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

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StudentEducationRequest {
        private UUID educationId;

        @Size(max = 200, message = "University must not exceed 200 characters")
        private String university;

        @Size(max = 200, message = "Degree must not exceed 200 characters")
        private String degree;

        @Size(max = 100, message = "Period must not exceed 100 characters")
        private String period;

        @Size(max = 5000, message = "Description must not exceed 5000 characters")
        private String description;
    }
}
