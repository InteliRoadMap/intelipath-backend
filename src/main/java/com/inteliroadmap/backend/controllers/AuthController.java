package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.request.ForgotPasswordRequest;
import com.inteliroadmap.backend.domain.dto.request.LoginRequest;
import com.inteliroadmap.backend.domain.dto.request.ResetPasswordRequest;
import com.inteliroadmap.backend.domain.dto.response.auth.RefreshResponse;
import com.inteliroadmap.backend.security.AuthenticationCookieService;
import com.inteliroadmap.backend.services.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller - Authentication API Endpoints
 * Provides endpoints:
 * - POST /api/v1/auth/login   - Sign in with an FPT account's username and password
 * - POST /api/v1/auth/refresh - Rotate a refresh token and issue new tokens
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Register and Login endpoints")
public class AuthController {

    private final AuthService authService;
    private final AuthenticationCookieService authenticationCookieService;

    /**
     * Signs in an FPT account provisioned by a counselor.
     *
     * @param request the submitted username and password
     * @param servletResponse response the refresh token cookie is attached to
     * @return response containing the newly issued access token
     */
    @PostMapping("/login")
    @Operation(
            summary = "Log in with username and password",
            description = "Authenticate an FPT account and issue a JWT access token plus a refreshToken HttpOnly cookie"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Logged in successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RefreshResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Invalid username or password"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Account is not active"
            )
    })
    public ResponseEntity<RefreshResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse servletResponse
    ) {
        log.info("AuthController: Login request received");
        RefreshResponse loginResponse = authService.login(request);
        authenticationCookieService.addRefreshTokenCookie(servletResponse, loginResponse.getRefreshToken());
        return ResponseEntity.ok(loginResponse);
    }

    /**
     * Rotates a valid refresh token stored in an HttpOnly cookie and returns a new access token.
     *
     * @param refreshToken refresh token read from HttpOnly cookie
     * @return response containing newly issued access token
     */
    @PostMapping("/refresh")
    @Operation(
            summary = "Refresh access token",
            description = "Read refreshToken from HttpOnly cookie, rotate it, and generate a new JWT access token"
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
            @CookieValue(name = AuthenticationCookieService.REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse servletResponse
    ) {
        log.info("AuthController: Refresh token request received");
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is missing");
        }

        RefreshResponse refreshResponse = authService.refreshAccount(refreshToken);
        authenticationCookieService.addRefreshTokenCookie(servletResponse, refreshResponse.getRefreshToken());
        return ResponseEntity.ok(refreshResponse);
    }

    /**
     * Starts the magic-link password reset. Always returns 200 with a neutral message, whether or
     * not the email maps to an account, so a caller cannot use this endpoint to discover which
     * addresses are registered.
     */
    @PostMapping("/forgot-password")
    @Operation(
            summary = "Request a password reset link",
            description = "If the email belongs to an account with a local password, emails a one-time reset link. Always returns 200."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Request accepted; a link is sent if the account exists")
    })
    public ResponseEntity<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        log.info("AuthController: Forgot-password request received");
        authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok("If an account exists for that email, a reset link has been sent.");
    }

    /**
     * Completes a password reset using the token from the emailed link.
     */
    @PostMapping("/reset-password")
    @Operation(
            summary = "Reset password with a token",
            description = "Validates the one-time token from the emailed link and sets a new password."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password reset successfully"),
            @ApiResponse(responseCode = "400", description = "Reset link is invalid or has expired")
    })
    public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        log.info("AuthController: Reset-password request received");
        authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok("Password has been reset successfully.");
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Logout",
            description = "Delete the refreshToken HttpOnly cookie and revoke it server-side when present"
    )
    public ResponseEntity<String> logout(
            @CookieValue(name = AuthenticationCookieService.REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse servletResponse
    ) {
        log.info("AuthController: Logout request received");
        authService.logout(refreshToken);
        authenticationCookieService.clearRefreshTokenCookie(servletResponse);
        return ResponseEntity.ok("Logged out successfully");
    }

}
