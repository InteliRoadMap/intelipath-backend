package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.response.RefreshResponse;
import com.inteliroadmap.backend.security.AuthenticationCookieService;
import com.inteliroadmap.backend.services.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller - Authentication API Endpoints
 * Provides endpoints:
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
