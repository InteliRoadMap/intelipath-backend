package com.inteliroadmap.backend.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum UserRole {
    STUDENT,
    COUSELOR,
    MENTOR;

    @JsonCreator
    public static UserRole fromString(String value) {
        return UserRole.valueOf(value.toUpperCase());
    }
}
