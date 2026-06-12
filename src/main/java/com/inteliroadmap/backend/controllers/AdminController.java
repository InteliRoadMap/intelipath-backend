package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.request.UpdateUserRoleRequest;
import com.inteliroadmap.backend.domain.dto.response.admin.AdminCourseMetricResponse;
import com.inteliroadmap.backend.domain.dto.response.admin.AdminSystemHealthResponse;
import com.inteliroadmap.backend.domain.dto.response.admin.AdminUserListItemResponse;
import com.inteliroadmap.backend.domain.dto.response.admin.AdminUserMetricResponse;
import com.inteliroadmap.backend.services.AdminDashboardService;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller - Admin Dashboard API Endpoints
 * Provides endpoints:
 * - GET /admin/dashboard/metrics/users   - Get total users metric
 * - GET /admin/dashboard/metrics/courses - Get total courses metric
 * - GET /admin/dashboard/metrics/health  - Get system health metric
 * - GET /admin/dashboard/users           - Get latest users list
 * - PATCH /admin/dashboard/users/{userId}/role - Update user role
 * - DELETE /admin/dashboard/users/{userId}      - Delete user
 */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Dashboard", description = "Admin dashboard endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminController {

    private final AdminDashboardService adminDashboardService;

    /**
     * GET /admin/dashboard/metrics/users - Get total users metric.
     *
     * @param authorizationHeader Authorization header containing Bearer access token
     * @return ResponseEntity containing AdminUserMetricResponse
     */
    @GetMapping("/metrics/users")
    @Operation(
            summary = "Get total users metric",
            description = "Get total users and growth percentage for admin dashboard"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "User metric retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminUserMetricResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized or invalid token"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Admin access required"
            )
    })
    public ResponseEntity<AdminUserMetricResponse> getUserMetric(
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        log.info("Admin Dashboard Controller: User metric request received");
        return ResponseEntity.ok(
                adminDashboardService.getUserMetrics(authorizationHeader)
        );
    }

    /**
     * GET /admin/dashboard/metrics/courses - Get total courses metric.
     *
     * @param authorizationHeader Authorization header containing Bearer access token
     * @return ResponseEntity containing AdminCourseMetricResponse
     */
    @GetMapping("/metrics/courses")
    @Operation(
            summary = "Get total courses metric",
            description = "Get total courses, status, and progress for admin dashboard"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Course metric retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminCourseMetricResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized or invalid token"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Admin access required"
            )
    })
    public ResponseEntity<AdminCourseMetricResponse> getCourseMetric(
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        log.info("Admin Dashboard Controller: Course metric request received");
        return ResponseEntity.ok(
                adminDashboardService.getCourseMetrics(authorizationHeader)
        );
    }

    /**
     * GET /admin/dashboard/metrics/health - Get system health metric.
     *
     * @param authorizationHeader Authorization header containing Bearer access token
     * @return ResponseEntity containing AdminSystemHealthResponse
     */
    @GetMapping("/metrics/health")
    @Operation(
            summary = "Get system health metric",
            description = "Get system uptime and status for admin dashboard"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "System health retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminSystemHealthResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized or invalid token"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Admin access required"
            )
    })
    public ResponseEntity<AdminSystemHealthResponse> getSystemHealth(
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        log.info("Admin Dashboard Controller: System health request received");
        return ResponseEntity.ok(
                adminDashboardService.getSystemHealth(authorizationHeader)
        );
    }

    /**
     * GET /admin/dashboard/users - Get latest users list.
     *
     * @param authorizationHeader Authorization header containing Bearer access token
     * @return ResponseEntity containing list of AdminUserListItemResponse
     */
    @GetMapping("/users")
    @Operation(
            summary = "Get latest users list",
            description = "Get latest registered users for admin dashboard user management table"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Users list retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminUserListItemResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized or invalid token"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Admin access required"
            )
    })
    public ResponseEntity<List<AdminUserListItemResponse>> getUsers(
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        log.info("Admin Dashboard Controller: Users list request received");
        return ResponseEntity.ok(
                adminDashboardService.getUsers(authorizationHeader)
        );
    }

    /**
     * PATCH /admin/dashboard/users/{userId}/role - Update a user's role.
     *
     * @param authorizationHeader Authorization header containing Bearer access token
     * @param userId              User id to update
     * @param request             Request payload containing the new role
     * @return ResponseEntity containing updated AdminUserListItemResponse
     */
    @PatchMapping("/users/{userId}/role")
    @Operation(
            summary = "Update user role",
            description = "Update a user's role for admin dashboard user management"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "User role updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminUserListItemResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid user id or request payload"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized or invalid token"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Admin access required"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found"
            )
    })
    public ResponseEntity<AdminUserListItemResponse> updateUserRole(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable String userId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Update user role request payload",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UpdateUserRoleRequest.class)
                    )
            )
            @RequestBody @Valid UpdateUserRoleRequest request
    ) {
        log.info("Admin Dashboard Controller: Update user role request received. userId: {}", userId);
        return ResponseEntity.ok(
                adminDashboardService.updateUserRole(authorizationHeader, userId, request)
        );
    }

    /**
     * DELETE /admin/dashboard/users/{userId} - Delete a user.
     *
     * @param authorizationHeader Authorization header containing Bearer access token
     * @param userId              User id to delete
     * @return ResponseEntity with no content
     */
    @DeleteMapping("/users/{userId}")
    @Operation(
            summary = "Delete user",
            description = "Delete a user from admin dashboard user management"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "User deleted successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid user id or admin cannot delete own account"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized or invalid token"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Admin access required"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found"
            )
    })
    public ResponseEntity<Void> deleteUser(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable String userId
    ) {
        log.info("Admin Dashboard Controller: Delete user request received. userId: {}", userId);
        adminDashboardService.deleteUser(authorizationHeader, userId);
        return ResponseEntity.noContent().build();
    }
}
