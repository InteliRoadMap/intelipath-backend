package com.inteliroadmap.backend.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum EvidenceStatus {
    PENDING,
    ACCEPTED,
    REJECTED;

    @JsonValue
    public static EvidenceStatus fromString(String value) {
        return EvidenceStatus.valueOf(value.toUpperCase());
    }
}
