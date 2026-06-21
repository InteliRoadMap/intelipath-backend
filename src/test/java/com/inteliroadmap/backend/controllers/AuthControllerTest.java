package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.response.RefreshResponse;
import com.inteliroadmap.backend.exceptions.GlobalExceptionHandler;
import com.inteliroadmap.backend.security.AuthenticationCookieService;
import com.inteliroadmap.backend.services.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerTest {

    private AuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AuthController(authService, mock(AuthenticationCookieService.class))
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void refreshReturnsJsonTokenPair() throws Exception {
        when(authService.refreshAccount(any())).thenReturn(RefreshResponse.builder()
                .accessToken("new-access")
                .refreshToken("new-refresh")
                .expiresIn("2026-06-04T14:00:00")
                .build());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"old-refresh\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken").value("new-access"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh"))
                .andExpect(jsonPath("$.expiresIn").value("2026-06-04T14:00:00"));
    }

    @Test
    void emptyRefreshTokenReturnsJsonBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Refresh token is required"));
    }

    @Test
    void missingRefreshTokenReturnsJsonBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Refresh token is required"));
    }

    @Test
    void invalidRefreshTokenReturnsJsonUnauthorizedWithoutRedirect() throws Exception {
        when(authService.refreshAccount(any())).thenThrow(
                new ResponseStatusException(UNAUTHORIZED, "Refresh token is invalid or expired")
        );

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"invalid\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(redirectedUrl(null))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Refresh token is invalid or expired"));
    }
}
