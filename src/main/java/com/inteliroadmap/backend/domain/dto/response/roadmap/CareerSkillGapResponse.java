package com.inteliroadmap.backend.domain.dto.response.roadmap;

import com.inteliroadmap.backend.domain.enums.ImportanceLevel;
import java.util.UUID;

/** A missing requirement from career_required_skills, independent of market volume. */
public record CareerSkillGapResponse(
        UUID skillId,
        String skillName,
        String category,
        ImportanceLevel importance
) {}
