package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.request.ChangePasswordRequest;
import com.inteliroadmap.backend.domain.dto.request.UserRequest;
import com.inteliroadmap.backend.domain.dto.request.SetupUserProfileRequest;
import com.inteliroadmap.backend.domain.dto.response.auth.UserResponse;
import com.inteliroadmap.backend.services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller - User API Endpoints
 * Provides endpoints:
 * - GET  /user/me      - Get current authenticated user info
 * - POST /user/profile - Get user info by email
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Info User", description = "User information endpoints")
@SecurityRequirement(name = "Bearer Authentication")
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
        log.info("UserController: Current user info request received");
        return ResponseEntity.ok(userService.getCurrentUser());
    }

    /**
     * POST /user/profile - Get user information by email.
     *
     * @param userRequest UserRequest containing user email
     * @return ResponseEntity containing UserResponse
     */
    @PostMapping("/by-email")
    @PreAuthorize("hasAnyRole('ADMIN', 'COUNSELOR', 'MENTOR')")
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
        log.info("UserController: User info request received for email: {}", userRequest.getEmail());
        return ResponseEntity.ok(userService.getUserByEmail(userRequest));
    }

    @PatchMapping("/profile")
    @Operation(
            summary = "Setup user profile",
            description = "Create or update profile details for the authenticated user"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "User profile updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid user profile payload"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized or invalid access token"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found"
            )
    })
    public ResponseEntity<UserResponse> setupUserProfile(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "User profile request payload",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SetupUserProfileRequest.class)
                    )
            )
            @RequestBody @Valid SetupUserProfileRequest request
    ) {
        log.info("UserController: User profile setup request received");
        return ResponseEntity.ok(userService.setupUserProfile(request));
    }

    @PatchMapping(value = "/profile/avatar", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Update user avatar",
            description = "Upload and update the avatar image for the authenticated user"
    )
    public ResponseEntity<UserResponse> updateAvatar(
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(userService.updateAvatar(file));
    }

    /**
     * PATCH /users/profile/password - Change the current password for the authenticated
     * account. Works the same for every role since it acts on the shared users table.
     */
    @PatchMapping("/profile/password")
    @Operation(
            summary = "Change password",
            description = "Change the password of the currently authenticated account, for any role"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password changed successfully"),
            @ApiResponse(responseCode = "400", description = "Current password is incorrect, new password is invalid, or the account has no local password"),
            @ApiResponse(responseCode = "401", description = "Unauthorized or invalid access token")
    })
    public ResponseEntity<String> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        log.info("UserController: Change password request received");
        userService.changePassword(request);
        return ResponseEntity.ok("Password changed successfully.");
    }
}
