package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.entity.SkillNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadmapPersonalizationFuzzyMatchTest {

    private static SkillNode node(String name) {
        return SkillNode.builder().nodeId(UUID.randomUUID()).nodeName(name).build();
    }

    @Test
    void broadApiEvidenceDoesNotProveSpecializedApiSkills() {
        SkillNode performance = node("API Performance");

        List<SkillNode> result = RoadmapPersonalizationServiceImpl.fuzzyMatchNodes(
                "api", Map.of("api performance", List.of(performance)), Map.of());

        assertTrue(result.isEmpty());
    }

    @Test
    void jsFormattingAliasesStillMatch() {
        SkillNode react = node("React");

        List<SkillNode> result = RoadmapPersonalizationServiceImpl.fuzzyMatchNodes(
                "react.js", Map.of("react", List.of(react)), Map.of());

        assertEquals(List.of(react), result);
    }
}
