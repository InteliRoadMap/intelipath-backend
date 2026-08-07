package com.inteliroadmap.backend.domain.dto.response.plan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** A required skill the plan skips, and what proved it could be skipped. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanSkipResponse {

    private UUID skillId;
    private String skillName;

    /** 1 AWARE, 2 PRACTICED, 3 APPLIED, 4 PROFESSIONAL. */
    private Short proficiency;

    /**
     * TRANSCRIPT | GITHUB | MENTOR, or null when the student declared it
     * themselves. Null is not a failure — it is a weaker claim, and the UI is
     * expected to show the difference.
     */
    private String verifiedBy;
}
