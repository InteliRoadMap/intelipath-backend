package com.inteliroadmap.backend.domain.dto.response.roadmap;

import com.inteliroadmap.backend.domain.dto.response.market.SkillDemandResponse;
import java.util.UUID;

/** Measurable skill metadata. Presence means career_required_skills owns the link. */
public record RoadmapSkillResponse(
        UUID skillId,
        String skillName,
        String category,
        Integer requiredProficiency,
        Short currentProficiency,
        String verifiedBy,
        SkillDemandResponse marketDemand
) {}
