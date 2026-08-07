package com.inteliroadmap.backend.domain.dto.response.market;

import java.util.UUID;

/** A career gap that also has measured demand in that career's postings. */
public record MarketSkillGapResponse(
        UUID skillId,
        String skillName,
        SkillDemandResponse demand
) {}
