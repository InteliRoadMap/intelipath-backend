package com.inteliroadmap.backend.domain.dto.response.portfolio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Portfolio-safe summary of one skill in the target career's core set. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoreSkillSummaryResponse {
    private String skillName;
    private String importance;
    private Short proficiency;
    private String verifiedBy;
    private Integer marketJobCount;
}
