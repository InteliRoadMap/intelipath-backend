package com.inteliroadmap.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    @Test
    void skipsPostRefreshEndpoint() {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(mock(JwtService.class), mock(com.inteliroadmap.backend.repositories.UserRepository.class));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/auth/refresh");

        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void doesNotSkipOtherRequests() {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(mock(JwtService.class), mock(com.inteliroadmap.backend.repositories.UserRepository.class));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/student/profile");

        assertFalse(filter.shouldNotFilter(request));
    }
}
