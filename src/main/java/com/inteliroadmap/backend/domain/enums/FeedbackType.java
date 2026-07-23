package com.inteliroadmap.backend.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum FeedbackType {
    GENERAL,
    SKILL,
    CAREER,
    PORTFOLIO;

    @JsonCreator
    public static FeedbackType fromString(String value) {
        return FeedbackType.valueOf(value.toUpperCase());
    }
}
