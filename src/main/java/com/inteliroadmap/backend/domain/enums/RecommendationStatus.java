package com.inteliroadmap.backend.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum RecommendationStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    EXPIRED;

    @JsonValue
    public static RecommendationStatus fromString(String value) {
        return RecommendationStatus.valueOf(value.toUpperCase());
    }
}
