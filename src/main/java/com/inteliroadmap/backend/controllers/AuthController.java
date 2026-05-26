package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.request.LoginRequest;
import com.inteliroadmap.backend.domain.dto.request.RefreshRequest;
import com.inteliroadmap.backend.domain.dto.request.RegisterRequest;
import com.inteliroadmap.backend.domain.dto.response.RefreshResponse;
import com.inteliroadmap.backend.domain.dto.response.RegisterResponse;
import com.inteliroadmap.backend.domain.dto.response.UserResponse;
import com.inteliroadmap.backend.domain.entity.User;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller - Authentication API Endpoints
 * Provides endpoints:
 * - POST /auth/register - Register new student account
 * - POST /auth/login    - Login with email and password
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Register and Login endpoints")
public class AuthController {

    private final AuthService authService;

    /**
     * POST /auth/register - Register new student account
     * @param registerRequest RegisterRequest containing email, password, fullName
     * @return ResponseEntity containing ApiResponse with UserResponse
     */
    @PostMapping("/register")
    @Operation(
            summary = "Register new account",
            description = "Register a new Student account using email and password"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Account registered successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Email already exists or invalid request payload"
            )
    })
    public ResponseEntity<RegisterResponse> registerAccount(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Register request payload",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RegisterRequest.class)
                    )
            )
            @RequestBody @Valid RegisterRequest registerRequest
    ) {
        log.info("Register request received for email: {}", registerRequest.getEmail());
        return ResponseEntity.ok(authService.registerAccount(registerRequest));
    }

    /**
     * POST /auth/login - Login with email and password
     * @param loginRequest LoginRequest containing email and password
     * @return ResponseEntity containing ApiResponse with UserResponse
     */
    @PostMapping("/login")
    @Operation(
            summary = "Login with email and password",
            description = "Authenticate user and receive JWT access token"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Login successful",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Wrong password"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Account is suspended"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Email not found"
            )
    })
    public ResponseEntity<UserResponse>loginAccount(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Login request payload",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = LoginRequest.class)
                    )
            )
            @RequestBody @Valid LoginRequest loginRequest
    ) {
        log.info("Login request received for email: {}", loginRequest.getEmail());
        return ResponseEntity.ok(authService.loginAccount(loginRequest));
    }

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
