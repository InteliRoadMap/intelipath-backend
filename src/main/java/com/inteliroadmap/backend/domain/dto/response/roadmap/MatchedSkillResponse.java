package com.inteliroadmap.backend.domain.dto.response.roadmap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchedSkillResponse {
    private String skillName;
    /** 1 AWARE · 2 PRACTICED · 3 APPLIED · 4 PROFESSIONAL. */
    private Integer proficiency;
    /** True when objective evidence backs it, not only a self-report. */
    private Boolean verified;
}
