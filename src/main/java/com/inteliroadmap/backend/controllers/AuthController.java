package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.request.RefreshRequest;
import com.inteliroadmap.backend.domain.dto.response.RefreshResponse;
import com.inteliroadmap.backend.security.AuthenticationCookieService;
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

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
     * Rotates a valid refresh token and returns a new access/refresh token pair.
     *
     * Reads a refresh token from the request body for backward compatibility, or from the
     * HTTP-only refresh cookie set by the OAuth2 login flow.
     * @return response containing newly issued tokens
     */
    @PostMapping("/refresh")
    @Operation(
            summary = "Refresh access token",
            description = "Rotate a valid refresh token and generate a new JWT access token"
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
                    required = false,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RefreshRequest.class)
                    )
            )
            @RequestBody(required = false) @Valid RefreshRequest refreshRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        log.info("Refresh token request received");
        RefreshRequest effectiveRequest = refreshRequest;
        if (effectiveRequest == null) {
            effectiveRequest = authenticationCookieService.getRefreshToken(request)
                    .map(token -> {
                        RefreshRequest cookieRequest = new RefreshRequest();
                        cookieRequest.setRefreshToken(token);
                        return cookieRequest;
                    })
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Refresh token is required"
                    ));
        }

        RefreshResponse refreshResponse = authService.refreshAccount(effectiveRequest);
        authenticationCookieService.addAuthenticationCookies(
                response,
                refreshResponse.getAccessToken(),
                refreshResponse.getRefreshToken()
        );
        return ResponseEntity.ok(refreshResponse);
    }

}
