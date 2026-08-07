package com.inteliroadmap.backend.domain.dto.response.roadmap;

/** Topic-only metadata; never represents a measurable catalog skill. */
public record RoadmapTopicResponse(
        int childTotal,
        int childCompleted,
        Integer hiddenChildren,
        String completionRule
) {}
