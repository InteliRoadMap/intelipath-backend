package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.response.auth.RefreshResponse;
import com.inteliroadmap.backend.exceptions.GlobalExceptionHandler;
import com.inteliroadmap.backend.security.AuthenticationCookieService;
import com.inteliroadmap.backend.services.AuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerTest {

    private AuthService authService;
    private AuthenticationCookieService authenticationCookieService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        authenticationCookieService = mock(AuthenticationCookieService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, authenticationCookieService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void refreshReturnsJsonTokenPair() throws Exception {
        when(authService.refreshAccount(eq("old-refresh"))).thenReturn(RefreshResponse.builder()
                .accessToken("new-access")
                .refreshToken("new-refresh")
                .expiresIn("2026-06-04T14:00:00Z")
                .build());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(AuthenticationCookieService.REFRESH_TOKEN_COOKIE_NAME, "old-refresh"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken").value("new-access"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.expiresIn").value("2026-06-04T14:00:00Z"));
    }

    @Test
    void blankRefreshTokenCookieReturnsJsonUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(AuthenticationCookieService.REFRESH_TOKEN_COOKIE_NAME, ""))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Refresh token is missing"));
    }

    @Test
    void missingRefreshTokenCookieReturnsJsonUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Refresh token is missing"));
    }

    @Test
    void invalidRefreshTokenReturnsJsonUnauthorizedWithoutRedirect() throws Exception {
        when(authService.refreshAccount(eq("invalid"))).thenThrow(
                new ResponseStatusException(UNAUTHORIZED, "Refresh token is invalid or expired")
        );

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(AuthenticationCookieService.REFRESH_TOKEN_COOKIE_NAME, "invalid"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(redirectedUrl(null))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Refresh token is invalid or expired"));
    }
}
