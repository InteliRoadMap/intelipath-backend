package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.services.SkillExtractionService;
import com.inteliroadmap.backend.scheduler.JobScrapingScheduler;
import com.inteliroadmap.backend.domain.dto.request.UpdateUserRoleRequest;
import com.inteliroadmap.backend.domain.dto.request.UpdateUserStatusRequest;
import com.inteliroadmap.backend.domain.dto.response.admin.AdminCourseMetricResponse;
import com.inteliroadmap.backend.domain.dto.response.admin.AdminSystemHealthResponse;
import com.inteliroadmap.backend.domain.dto.response.admin.AdminUserListItemResponse;
import com.inteliroadmap.backend.domain.dto.response.admin.AdminUserMetricResponse;
import com.inteliroadmap.backend.services.AdminService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final SkillExtractionService skillExtractionService;
    private final JobScrapingScheduler jobScrapingScheduler;

    /**
     * POST /admin/dashboard/trigger-skill-extraction - Manually trigger skill extraction job.
     */
    @PostMapping("/trigger-skill-extraction")
    @Operation(summary = "Trigger Skill Extraction", description = "Manually triggers the background AI skill extraction job.")
    public ResponseEntity<String> triggerSkillExtraction() {
        log.info("AdminController: Manual trigger for skill extraction received");
        try {
            skillExtractionService.extractAndRebuildSkillTrends();
            return ResponseEntity.ok("Skill extraction via AI Service completed successfully.");
        } catch (Exception e) {
            log.error("AdminController: Error extracting skills", e);
            return ResponseEntity.internalServerError().body("Error during extraction: " + e.getMessage());
        }
    }

    /**
     * POST /admin/dashboard/trigger-job-scraper - Manually trigger a scraping job.
     * {@code source} selects the board (topcv | itviec); defaults to topcv for
     * backward compatibility. ITviec is IT-focused and doesn't need a proxy;
     * TopCV sits behind Cloudflare and may require SCRAPER_PROXY.
     */
    @PostMapping("/trigger-job-scraper")
    @Operation(summary = "Trigger Job Scraper", description = "Manually triggers the Python AI scraper job to fetch new recruitments. Use source=topcv or source=itviec.")
    public ResponseEntity<String> triggerJobScraper(
            @RequestParam(name = "source", defaultValue = "topcv") String source) {
        log.info("AdminController: Manual trigger for job scraper received (source={})", source);
        final JobScrapingScheduler.Source target;
        try {
            target = JobScrapingScheduler.Source.valueOf(source.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Unknown source '" + source + "'. Use 'topcv' or 'itviec'.");
        }
        try {
            int posts = jobScrapingScheduler.fetchJobs(target);
            return ResponseEntity.ok(target + " scraper finished — imported " + posts + " job post(s).");
        } catch (IllegalStateException e) {
            // The scrape ran as a job and either failed or outlived its polling budget.
            log.error("AdminController: {} scrape did not complete: {}", target, e.getMessage());
            return ResponseEntity.status(504).body(e.getMessage());
        } catch (org.springframework.web.client.ResourceAccessException e) {
            // Timeouts / connection issues reaching the AI service itself surface here.
            log.error("AdminController: Job scraper could not reach the AI service", e);
            return ResponseEntity.status(504).body(
                target + " scraper could not reach the AI service — check that it is running.");
        } catch (org.springframework.web.client.RestClientResponseException e) {
            // The AI service answered with an error (e.g. 502 Cloudflare block for TopCV).
            // Surface its message cleanly instead of dumping a stack trace.
            log.warn("AdminController: {} scraper rejected by AI service ({}): {}",
                target, e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode().value()).body(
                target + " scraper could not run: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("AdminController: Error triggering job scraper", e);
            return ResponseEntity.internalServerError().body("Error during trigger: " + e.getMessage());
        }
    }

    /**
     * GET /admin/dashboard/metrics/users - Get total users metric.
     *
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
    ) {
        log.info("AdminController: User metric request received");
        return ResponseEntity.ok(
                adminService.getUserMetrics()
        );
    }

    /**
     * GET /admin/dashboard/metrics/courses - Get total courses metric.
     *
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
    ) {
        log.info("AdminController: Course metric request received");
        return ResponseEntity.ok(
                adminService.getCourseMetrics()
        );
    }

    /**
     * GET /admin/dashboard/metrics/health - Get system health metric.
     *
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
    ) {
        log.info("AdminController: System health request received");
        return ResponseEntity.ok(
                adminService.getSystemHealth()
        );
    }

    /**
     * GET /admin/dashboard/users - Get latest users list.
     *
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
    ) {
        log.info("AdminController: Users list request received");
        return ResponseEntity.ok(
                adminService.getUsers()
        );
    }

    /**
     * PATCH /admin/dashboard/users/{userId}/role - Update a user's role.
     *
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
        log.info("AdminController: Update user role request received. userId: {}", userId);
        return ResponseEntity.ok(
                adminService.updateUserRole(userId, request)
        );
    }

    /**
     * PATCH /admin/dashboard/users/{userId}/status - Suspend / reactivate a user.
     *
     * @param userId              User id to update
     * @param request             Request payload containing the new account status
     * @return ResponseEntity containing updated AdminUserListItemResponse
     */
    @PatchMapping("/users/{userId}/status")
    @Operation(
            summary = "Update user account status",
            description = "Suspend, deactivate or reactivate a user's account (soft account control)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "User status updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminUserListItemResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Invalid user id or admin cannot suspend own account"),
            @ApiResponse(responseCode = "401", description = "Unauthorized or invalid token"),
            @ApiResponse(responseCode = "403", description = "Admin access required"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<AdminUserListItemResponse> updateUserStatus(
            @PathVariable String userId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Update user status request payload",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UpdateUserStatusRequest.class)
                    )
            )
            @RequestBody @Valid UpdateUserStatusRequest request
    ) {
        log.info("AdminController: Update user status request received. userId: {}", userId);
        return ResponseEntity.ok(
                adminService.updateUserStatus(userId, request)
        );
    }

    /**
     * DELETE /admin/dashboard/users/{userId} - Delete a user.
     *
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
            @PathVariable String userId
    ) {
        log.info("AdminController: Delete user request received. userId: {}", userId);
        adminService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }
}
