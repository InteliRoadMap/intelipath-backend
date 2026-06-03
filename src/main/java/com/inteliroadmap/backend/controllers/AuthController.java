package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.request.RefreshRequest;
import com.inteliroadmap.backend.domain.dto.response.RefreshResponse;
import com.inteliroadmap.backend.services.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller - Authentication API Endpoints
 * Provides endpoints:
 * - POST /auth/register - Register new student account
 * - POST /auth/login    - Login with email and password
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Register and Login endpoints")
public class AuthController {

    private final AuthService authService;

    /**
     * POST /auth/refresh - Refresh access token using refresh token
     * @param refreshRequest RefreshRequest containing refresh token
     * @return ResponseEntity containing new access token
     */
    @PostMapping("/refresh")
    @Operation(
            summary = "Refresh access token",
            description = "Generate new JWT access token using refresh token"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Access token refreshed successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RefreshResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Invalid or expired refresh token"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Refresh token or user not found"
            )
    })
    public ResponseEntity<RefreshResponse> refreshAccount(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Refresh token payload",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RefreshRequest.class)
                    )
            )
            @RequestBody @Valid RefreshRequest refreshRequest
    ) {
        log.info("Refresh token request received");
        return ResponseEntity.ok(
                authService.refreshAccount(refreshRequest)
        );
    }

}
