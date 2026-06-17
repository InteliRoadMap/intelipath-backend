package com.inteliroadmap.backend.exceptions;

public class BlockedIpException extends RuntimeException {
    public BlockedIpException(String message) {
        super(message);
    }
}
