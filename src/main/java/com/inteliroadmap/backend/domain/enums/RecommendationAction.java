package com.inteliroadmap.backend.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum RecommendationAction {
    MARK_COMPLETE,
    SKIP,
    UNLOCK,
    PRIORITIZE,
    ADD,
    REMOVE;

    @JsonValue
    public static RecommendationAction fromString(String value) {
        return RecommendationAction.valueOf(value.toUpperCase());
    }
}
