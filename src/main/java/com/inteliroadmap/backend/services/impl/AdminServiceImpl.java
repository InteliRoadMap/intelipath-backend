package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.request.UpdateUserRoleRequest;
import com.inteliroadmap.backend.domain.dto.response.admin.AdminCourseMetricResponse;
import com.inteliroadmap.backend.domain.dto.response.admin.AdminSystemHealthResponse;
import com.inteliroadmap.backend.domain.dto.response.admin.AdminUserListItemResponse;
import com.inteliroadmap.backend.domain.dto.response.admin.AdminUserMetricResponse;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.mappers.AdminMapper;
import com.inteliroadmap.backend.repositories.CareerRoleRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.security.JwtService;
import com.inteliroadmap.backend.services.AdminService;
import com.inteliroadmap.backend.utils.BearerTokenUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
    private final JwtService  jwtService;
    private final AdminMapper adminMapper;

    /**
     * Retrieves the total users metric for the admin dashboard.
     *
     * @param authorizationHeader the authorization header containing the admin's JWT token
     * @return the user metric data containing total user count and growth percentage
     */
    @Transactional
    @Override
    public AdminUserMetricResponse getUserMetrics(String authorizationHeader) {
        validateAdmin(authorizationHeader);

        log.info("AdminServiceImpl: Get user metric data");

        // Return user metrics with total count and a default growth percentage of 12%
        return adminMapper.toUserMetricResponse(userRepository.count(), 12);
    }

    /**
     * Retrieves the total learning paths and courses metric for the admin dashboard.
     *
     * @param authorizationHeader the authorization header containing the admin's JWT token
     * @return the course metric data containing total active courses
     */
    @Transactional
    @Override
    public AdminCourseMetricResponse getCourseMetrics(String authorizationHeader) {
        validateAdmin(authorizationHeader);

        log.info("AdminServiceImpl: Get course metric data");

        // Retrieve total number of career roles as active courses
        long total = careerRoleRepository.count();

        // Return course metrics including total, active status, and arbitrary percentage
        return adminMapper.toCourseMetricResponse(total, "ACTIVE", 78);
    }

    /**
     * Retrieves the system health status for the admin dashboard.
     *
     * @param authorizationHeader the authorization header containing the admin's JWT token
     * @return the system health response containing uptime and status
     */
    @Override
    public AdminSystemHealthResponse getSystemHealth(String authorizationHeader) {
        validateAdmin(authorizationHeader);

        log.info("AdminServiceImpl: Get system health");

        // Return static system health status indicating 99.9% uptime and ONLINE status
        return adminMapper.toSystemHealthResponse(99.9, "ONLINE");
    }

    /**
     * Retrieves a list of all users for the admin dashboard user table.
     *
     * @param authorizationHeader the authorization header containing the admin's JWT token
     * @return a list of user details including roles and emails
     */
    @Transactional
    @Override
    public List<AdminUserListItemResponse> getUsers(String authorizationHeader) {
        validateAdmin(authorizationHeader);

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
     * @param authorizationHeader the authorization header containing the admin's JWT token
     * @param userId the unique identifier of the user to update
     * @param request the request object containing the new role
     * @return the updated user's details
     */
    @Transactional
    @Override
    public AdminUserListItemResponse updateUserRole(String authorizationHeader, String userId, UpdateUserRoleRequest request) {

        validateAdmin(authorizationHeader);

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
     * Deletes a user by their ID. An admin cannot delete their own account.
     *
     * @param authorizationHeader the authorization header containing the admin's JWT token
     * @param userId the unique identifier of the user to delete
     * @throws ResourceNotFoundException if the user attempts to delete their own account
     */
    @Transactional
    @Override
    public void deleteUser(String authorizationHeader, String userId) {validateAdmin(authorizationHeader);

        log.info("AdminServiceImpl: Delete user. userId: {}", userId);

        // Extract the email of the admin performing the request to prevent self-deletion
        String currentEmail = jwtService.extractEmail(BearerTokenUtil.extractToken(authorizationHeader));
        User user = findUserById(userId);

        // Ensure the admin is not trying to delete their own account
        if (user.getEmail().equals(currentEmail)) {
            throw new ResourceNotFoundException("Admin cannot delete own account");
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
            throw new ResourceNotFoundException("Invalid user id");
        }
    }

    /**
     * Validates that the provided authorization header contains a valid admin token.
     *
     * @param authorizationHeader the HTTP authorization header
     * @throws ResourceNotFoundException if the token is invalid or the user is not an admin
     */
    private void validateAdmin(String authorizationHeader) {
        // Extract the token and role from the authorization header
        String token = BearerTokenUtil.extractToken(authorizationHeader);
        String role = jwtService.extractRole(token);

        // Verify the token is valid and the user holds the ADMIN role
        if (!jwtService.isTokenValid(token) || !"ADMIN".equals(role)) {
            log.warn("AdminServiceImpl: Access denied. role: {}", role);
            throw new ResourceNotFoundException("Access denied");
        }
    }

}
