package com.inteliroadmap.backend.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum FeedbackType {
    GENERAL,
    SKILL,
    CAREER,
    PORTFOLIO;

    @JsonCreator
    public static ImportanceLevel fromString(String value) {
        return ImportanceLevel.valueOf(value.toUpperCase());
    }
}
