package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.request.UserRequest;
import com.inteliroadmap.backend.domain.dto.response.UserResponse;
import com.inteliroadmap.backend.services.UserService;
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
 * Controller - User API Endpoints
 * Provides endpoints:
 * - GET  /user/me      - Get current authenticated user info
 * - POST /user/profile - Get user info by email
 */
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Info User", description = "User information endpoints")
public class UserController {

    private final UserService userService;

    /**
     * GET /user/me - Get current authenticated user information.
     *
     * @return ResponseEntity containing UserResponse of current authenticated user
     */
    @GetMapping("/me")
    @Operation(
            summary = "Get current user info",
            description = "Get authenticated user information from JWT token"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "User information retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized or invalid token"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found"
            )
    })
    public ResponseEntity<UserResponse> getCurrentUser() {
        log.info("Current user info request received");
        return ResponseEntity.ok(userService.getCurrentUser());
    }

    /**
     * POST /user/profile - Get user information by email.
     *
     * @param userRequest UserRequest containing user email
     * @return ResponseEntity containing UserResponse
     */
    @PostMapping("/by-email")
    @Operation(
            summary = "Get user info by email",
            description = "Get user information using email"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "User information retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request payload"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found"
            )
    })
    public ResponseEntity<UserResponse> getUserByEmail(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "User email request payload",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserRequest.class)
                    )
            )
            @RequestBody @Valid UserRequest userRequest
    ) {
        log.info("User info request received for email: {}", userRequest.getEmail());
        return ResponseEntity.ok(userService.getUserByEmail(userRequest));
    }

    @PatchMapping("/profile")
    @Operation(summary = "Setup user profile")
    public ResponseEntity<UserResponse> setupUserProfile(
            @RequestBody @Valid com.inteliroadmap.backend.domain.dto.request.SetupUserProfileRequest request
    ) {
        log.info("User profile setup request received");
        return ResponseEntity.ok(userService.setupUserProfile(request));
    }
}