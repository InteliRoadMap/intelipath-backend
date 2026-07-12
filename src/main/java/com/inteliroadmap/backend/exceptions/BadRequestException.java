package com.inteliroadmap.backend.exceptions;

/** Thrown when a request is malformed or semantically invalid (maps to HTTP 400). */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
