package com.inteliroadmap.backend.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ReviewStatus {
    PENDING,
    REVIEWED,
    REJECTED;

    @JsonValue
    public static ReviewStatus fromString(String value) {
        return ReviewStatus.valueOf(value.toUpperCase());
    }
}
