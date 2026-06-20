package com.inteliroadmap.backend.exceptions.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {


    EMAIL_ALREADY_EXISTS(400, "Email already exists"),
    EMAIL_NOT_FOUND(404, "Email not found"),

    WRONG_PASSWORD(401, "Wrong password"),
    ACCOUNT_SUSPENDED(403, "Account suspended"),
    REGISTER_SUCCESSFULLY(200, "Register successful"),

    UNAUTHORIZED(401, "Unauthorized"),

    INTERNAL_SERVER_ERROR(500, "Internal server error"),

    INVALID_OTP_CODE(401, "Unauthorized");

    private final int status;
    private final String message;
}
