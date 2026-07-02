package com.inteliroadmap.backend.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum RecommendationType {
    SKIP_KNOWN_SKILLS,
    FAST_TRACK,
    CHANGE_PATH,
    ADD_ADVANCED_TOPICS;

    @JsonValue
    public static RecommendationType fromString(String value) {
        return RecommendationType.valueOf(value.toUpperCase());
    }
}
