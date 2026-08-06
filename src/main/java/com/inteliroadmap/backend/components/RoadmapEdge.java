package com.inteliroadmap.backend.components;

import java.util.UUID;

/**
 * One connection between two roadmap nodes, computed for a specific student.
 *
 * @param kind {@code SEQUENCE} for "learn this before that" (drawn solid),
 *        {@code HIERARCHY} for "this belongs to that topic" (drawn dashed)
 * @param reason one sentence naming the evidence behind the ordering, e.g.
 *        "React trước Vue — 41% tin tuyển dụng gần đây yêu cầu React, Vue 6%".
 *        Never blank: an edge nobody can justify is exactly the thing this
 *        change exists to remove.
 */
public record RoadmapEdge(UUID source, UUID target, String kind, String reason) {

    public static final String KIND_SEQUENCE = "SEQUENCE";
    public static final String KIND_HIERARCHY = "HIERARCHY";
}
