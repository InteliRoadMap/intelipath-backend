package com.inteliroadmap.backend.security;

import com.inteliroadmap.backend.exceptions.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Resolves the currently authenticated principal from the Spring Security
 * context. The JWT filter stores the user's email as the authentication name,
 * so services read the current user from here instead of re-parsing the raw
 * Authorization header — keeping HTTP concerns out of the service layer.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /** @return the authenticated user's email, or throws 401 if unauthenticated. */
    public static String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || authentication.getName().isBlank()) {
            throw new UnauthorizedException("No authenticated user in security context");
        }
        return authentication.getName();
    }
}
