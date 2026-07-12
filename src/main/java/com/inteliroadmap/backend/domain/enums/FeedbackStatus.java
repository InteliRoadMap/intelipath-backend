package com.inteliroadmap.backend.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum FeedbackStatus {
    NEW,
    READ,
    DELETED;

    @JsonValue
    public static FeedbackStatus fromString(String value){
        return FeedbackStatus.valueOf(value.toUpperCase());
    }
}
