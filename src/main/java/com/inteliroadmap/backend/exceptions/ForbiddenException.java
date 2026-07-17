package com.inteliroadmap.backend.exceptions;

/** Thrown when an authenticated caller lacks permission for the action (maps to HTTP 403). */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }

    public ForbiddenException(String message, Throwable cause) {
        super(message, cause);
    }
}
