package com.inteliroadmap.backend.utils;

import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;

public final class BearerTokenUtil {

    private static final String BEARER_PREFIX = "Bearer ";

    private BearerTokenUtil() {
    }

    public static String extractToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new ResourceNotFoundException("Missing or invalid Authorization header");
        }

        return authorizationHeader.substring(BEARER_PREFIX.length());
    }
}
