package com.inteliroadmap.backend.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ImportanceLevel {
    LOW,
    AVG,
    HIGH;

    @JsonCreator
    public static ImportanceLevel fromString(String value) {
        return ImportanceLevel.valueOf(value.toUpperCase());
    }
}
