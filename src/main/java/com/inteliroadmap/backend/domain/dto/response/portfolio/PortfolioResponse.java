package com.inteliroadmap.backend.domain.dto.response.portfolio;

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
public class PortfolioResponse {
    private UserInfoResponse userInfo;
    private PortfolioConfigResponse config;
    private List<StudentSkillResponse> skills;
    private List<PortfolioProjectResponse> projects;
    private List<StudentEducationResponse> education;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UserInfoResponse {
        // The slug addresses the portfolio, but feedback is addressed to a person:
        // a viewer who arrived by slug still needs the id to write back.
        private UUID userId;
        private String fullName;
        private String bio;
        private String email;
        private String portfolioSlug;
        private String university;
        private String avatarUrl;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PortfolioConfigResponse {
        private String theme;
        private Map<String, Object> themeColors;
        private Map<String, Object> fonts;
        private Map<String, Object> heroSection;
        private Map<String, Object> skillsSection;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StudentSkillResponse {
        private String skillName;
        private String customDescription;
        private String techStack;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PortfolioProjectResponse {
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
    public static class StudentEducationResponse {
        private UUID educationId;
        private String university;
        private String degree;
        private String period;
        private String description;
    }
}
