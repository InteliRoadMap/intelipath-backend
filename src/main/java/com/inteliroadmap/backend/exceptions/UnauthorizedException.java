package com.inteliroadmap.backend.exceptions;

/** Thrown when the caller is not authenticated or the credentials are invalid (maps to HTTP 401). */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}
