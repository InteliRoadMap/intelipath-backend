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
public class StudentSkillResponse {
    private String skillName;
    private String customDescription;
    private String techStack;
    // True only when a GitHub repository or a passed subject backs the skill.
    // A skill the student merely declared about themselves stays false, so a
    // viewer is never shown an unproven claim as if it were verified.
    private boolean verified;
    // Which source earned the badge: GITHUB_PROJECT, TRANSCRIPT or MANUAL.
    private EvidenceType evidenceSource;
}
