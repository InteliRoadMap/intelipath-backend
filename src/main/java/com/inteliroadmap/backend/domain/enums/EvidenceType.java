package com.inteliroadmap.backend.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum EvidenceType {
    GITHUB_PROJECT,
    TRANSCRIPT,
    CHAT_FILE,
    MANUAL;

    @JsonValue
    public static EvidenceType fromString(String value) {
        return EvidenceType.valueOf(value.toUpperCase());
    }
}
