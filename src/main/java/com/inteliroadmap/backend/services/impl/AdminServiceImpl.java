package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.response.admin.DbPool;
import com.inteliroadmap.backend.domain.dto.response.admin.Memory;
import com.inteliroadmap.backend.domain.dto.response.admin.ServiceStatus;

import com.inteliroadmap.backend.domain.dto.request.UpdateUserRoleRequest;
import com.inteliroadmap.backend.domain.dto.request.UpdateUserStatusRequest;
import com.inteliroadmap.backend.domain.enums.UserStatus;
import com.inteliroadmap.backend.domain.dto.response.admin.AdminCourseMetricResponse;
import com.inteliroadmap.backend.domain.dto.response.admin.AdminSystemHealthResponse;
import com.inteliroadmap.backend.domain.dto.response.admin.AdminUserListItemResponse;
import com.inteliroadmap.backend.domain.dto.response.admin.AdminUserMetricResponse;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.exceptions.BadRequestException;
import com.inteliroadmap.backend.exceptions.ForbiddenException;
import com.inteliroadmap.backend.mappers.AdminMapper;
import com.inteliroadmap.backend.repositories.CareerRoleRepository;
import com.inteliroadmap.backend.repositories.SkillNodeRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.security.SecurityUtils;
import com.inteliroadmap.backend.ai.client.AiServiceClient;
import com.inteliroadmap.backend.services.AdminService;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of the AdminService interface.
 * Handles administrative operations such as user management, dashboard metrics retrieval,
 * and system health monitoring.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final CareerRoleRepository careerRoleRepository;
    private final SkillNodeRepository skillNodeRepository;
    private final AdminMapper adminMapper;
    private final DataSource dataSource;
    private final AiServiceClient aiServiceClient;

    /**
     * Retrieves the total users metric for the admin dashboard.
     *
     * @return the user metric data containing total user count and growth percentage
     */
    @Transactional
    @Override
    public AdminUserMetricResponse getUserMetrics() {


        log.info("AdminServiceImpl: Get user metric data");

        // Real month-over-month growth: sign-ups in the last 30 days vs the 30 before.
        LocalDateTime now = LocalDateTime.now();
        long last30 = userRepository.countByCreatedAtAfter(now.minusDays(30));
        long prev30 = userRepository.countByCreatedAtBetween(now.minusDays(60), now.minusDays(30));
        int growth = prev30 == 0
                ? (last30 > 0 ? 100 : 0)
                : (int) Math.round((last30 - prev30) * 100.0 / prev30);

        return adminMapper.toUserMetricResponse(userRepository.count(), growth);
    }

    /**
     * Retrieves the total learning paths and courses metric for the admin dashboard.
     *
     * @return the course metric data containing total active courses
     */
    @Transactional
    @Override
    public AdminCourseMetricResponse getCourseMetrics() {


        log.info("AdminServiceImpl: Get course metric data");

        // "Courses" = career roles. Progress = how many of them actually have a
        // roadmap built out (at least one skill node), not an arbitrary number.
        long total = careerRoleRepository.count();
        long withRoadmap = skillNodeRepository.countDistinctCareersWithNodes();
        int progress = total == 0 ? 0 : (int) Math.round(withRoadmap * 100.0 / total);
        String status = total > 0 ? "ACTIVE" : "EMPTY";

        return adminMapper.toCourseMetricResponse(total, status, progress);
    }

    /**
     * Retrieves the system health status for the admin dashboard.
     *
     * @return the system health response containing uptime and status
     */
    @Override
    public AdminSystemHealthResponse getSystemHealth() {


        log.info("AdminServiceImpl: Get system health");

        List<ServiceStatus> services = new ArrayList<>();

        // API: if this request is being served, the API layer is up.
        services.add(new ServiceStatus("API", true));

        // Database: a lightweight query proves connectivity.
        boolean dbUp;
        try {
            userRepository.count();
            dbUp = true;
        } catch (Exception e) {
            log.warn("AdminServiceImpl: Database health check failed: {}", e.getMessage());
            dbUp = false;
        }
        services.add(new ServiceStatus("Database", dbUp));

        // AI Service: ping its root endpoint with a short timeout.
        services.add(new ServiceStatus("AI Service", aiServiceClient.isHealthy()));

        int up = (int) services.stream()
                .filter(ServiceStatus::isUp)
                .count();

        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMb = runtime.maxMemory() / (1024 * 1024);

        String version = getClass().getPackage().getImplementationVersion();

        return AdminSystemHealthResponse.builder()
                .status(up == services.size() ? "Operational" : "Degraded")
                .servicesUp(up)
                .servicesTotal(services.size())
                .services(services)
                .uptimeMillis(ManagementFactory.getRuntimeMXBean().getUptime())
                .version(version != null ? version : "dev")
                .javaVersion(System.getProperty("java.version"))
                .db(readDbPool())
                .memory(new Memory(usedMb, maxMb))
                .build();
    }

    /** Live HikariCP connection-pool stats; nulls out gracefully if unavailable. */
    private DbPool readDbPool() {
        try {
            if (dataSource instanceof HikariDataSource hikari && hikari.getHikariPoolMXBean() != null) {
                var pool = hikari.getHikariPoolMXBean();
                return DbPool.builder()
                        .active(pool.getActiveConnections())
                        .idle(pool.getIdleConnections())
                        .total(pool.getTotalConnections())
                        .max(hikari.getMaximumPoolSize())
                        .build();
            }
        } catch (Exception e) {
            log.warn("AdminServiceImpl: Could not read DB pool stats: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Retrieves a list of all users for the admin dashboard user table.
     *
     * @return a list of user details including roles and emails
     */
    @Transactional
    @Override
    public List<AdminUserListItemResponse> getUsers() {


        log.info("AdminServiceImpl: Get users list");

        // Fetch all users from the database, map to list item responses, and collect to a list
        return userRepository.findAllUsers()
                .stream()
                .map(adminMapper::toUserListItem)
                .toList();
    }

    /**
     * Updates the role of a specific user.
     *
     * @param userId the unique identifier of the user to update
     * @param request the request object containing the new role
     * @return the updated user's details
     */
    @Transactional
    @Override
    public AdminUserListItemResponse updateUserRole(String userId, UpdateUserRoleRequest request) {



        log.info("AdminServiceImpl: Update user role. userId: {}, role: {}", userId, request.getRole());

        // Find the target user by ID and set their new role from the request
        User user = findUserById(userId);
        user.setRole(request.getRole());

        // Save the updated user to the database
        User updatedUser = userRepository.save(user);
        log.info("AdminServiceImpl: User role updated successfully. email: {}, role: {}",
                updatedUser.getEmail(), updatedUser.getRole());

        // Return the updated user information mapped to a list item response
        return adminMapper.toUserListItem(updatedUser);
    }

    /**
     * Updates the account status of a user (e.g. suspend / reactivate). An admin
     * cannot suspend or deactivate their own account.
     *
     * @param userId the unique identifier of the user to update
     * @param request the request object containing the new {@link com.inteliroadmap.backend.domain.enums.UserStatus}
     * @return the updated user's details
     */
    @Transactional
    @Override
    public AdminUserListItemResponse updateUserStatus(String userId, UpdateUserStatusRequest request) {



        log.info("AdminServiceImpl: Update user status. userId: {}, status: {}", userId, request.getStatus());

        String currentEmail = SecurityUtils.getCurrentUserEmail();
        User user = findUserById(userId);

        // An admin must not lock themselves out by suspending/deactivating their own account.
        if (user.getEmail().equals(currentEmail)
                && request.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenException("Admin cannot suspend own account");
        }

        user.setUserStatus(request.getStatus());
        User updatedUser = userRepository.save(user);
        log.info("AdminServiceImpl: User status updated successfully. email: {}, status: {}",
                updatedUser.getEmail(), updatedUser.getUserStatus());

        return adminMapper.toUserListItem(updatedUser);
    }

    /**
     * Deletes a user by their ID. An admin cannot delete their own account.
     *
     * @param userId the unique identifier of the user to delete
     * @throws ResourceNotFoundException if the user attempts to delete their own account
     */
    @Transactional
    @Override
    public void deleteUser(String userId) {

        log.info("AdminServiceImpl: Delete user. userId: {}", userId);

        // Extract the email of the admin performing the request to prevent self-deletion
        String currentEmail = SecurityUtils.getCurrentUserEmail();
        User user = findUserById(userId);

        // Ensure the admin is not trying to delete their own account
        if (user.getEmail().equals(currentEmail)) {
            throw new ForbiddenException("Admin cannot delete own account");
        }

        // Proceed to delete the user from the database
        userRepository.delete(user);

        log.info("AdminServiceImpl: User deleted successfully. email: {}", user.getEmail());
    }

    /**
     * Helper method to find a user by their UUID string.
     *
     * @param userId the string representation of the user's UUID
     * @return the User entity if found
     * @throws ResourceNotFoundException if the user is not found or the ID is invalid
     */
    private User findUserById(String userId) {
        try {
            // Attempt to parse the provided ID string into a UUID
            UUID id = UUID.fromString(userId);
            // Search for the user in the database
            Optional<User> userOptional = userRepository.findById(id);

            // Throw exception if the user does not exist
            if (userOptional.isEmpty()) {
                throw new ResourceNotFoundException("User not found");
            }

            return userOptional.get();
        } catch (IllegalArgumentException e) {
            // Handle cases where the provided ID string is not a valid UUID format
            throw new BadRequestException("Invalid user id");
        }
    }

}
