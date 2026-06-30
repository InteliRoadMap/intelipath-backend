package com.inteliroadmap.backend.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum StageType {
    FOUNDATION,
    CORE,
    PRACTICAL,
    ADVANCED,
    JOB_READY;

    @JsonValue
    public static StageType fromString(String value) {
        return StageType.valueOf(value.toUpperCase());
    }

    @JsonValue
    public String toFrontendValue() {
        return switch (this) {
            case FOUNDATION -> "Foundation";
            case CORE -> "Core";
            case PRACTICAL -> "Practical";
            case ADVANCED -> "Advanced";
            case JOB_READY -> "Job Ready";
        };
    }
}
