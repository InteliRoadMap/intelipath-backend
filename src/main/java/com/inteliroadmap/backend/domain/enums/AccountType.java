package com.inteliroadmap.backend.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Where an account comes from, which decides whether FPT-internal material is offered.
 *
 * FPT accounts are provisioned by a counselor and sign in with a username + password.
 * Everyone who arrives through GitHub/Google OAuth is OTHER.
 */
public enum AccountType {
    FPT,
    OTHER;

    @JsonCreator
    public static AccountType fromString(String value) {
        return AccountType.valueOf(value.toUpperCase());
    }
}
