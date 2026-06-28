package com.inteliroadmap.backend.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum NodeType {
    UNIDENTIFIED,
    FOUNDATION,
    CORE,
    PRACTICAL,
    ADVANCED,
    JOB_READY;

    @JsonValue
    public static NodeType fromString(String value) {
        return NodeType.valueOf(value.toUpperCase());
    }
}
