package com.inteliroadmap.backend.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ReviewStatus {
    PENDING,
    REVIEWED,
    REJECTED;

    @JsonCreator
    public static ReviewStatus fromString(String value) {
        return ReviewStatus.valueOf(value.toUpperCase());
    }
}
