package com.inteliroadmap.backend.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Response status used by the student dashboard roadmap.
 */
public enum RoadmapStepStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    LOCKED;

    @JsonValue
    public String toFrontendValue() {
        return switch (this) {
            case COMPLETED -> "completed";
            case IN_PROGRESS -> "current";
            case NOT_STARTED, LOCKED -> "locked";
        };
    }
}
