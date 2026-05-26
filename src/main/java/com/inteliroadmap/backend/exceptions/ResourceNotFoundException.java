package com.inteliroadmap.backend.exceptions;



public class ResourceNotFoundException extends RuntimeException {

    /**
     *@param message: description error
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }

    /**
     * @param message: description error
     * @param cause  : root cause
     */
    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
