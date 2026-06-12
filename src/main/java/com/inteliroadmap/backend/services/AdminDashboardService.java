package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.UpdateUserRoleRequest;
import com.inteliroadmap.backend.domain.dto.response.admin.AdminCourseMetricResponse;
import com.inteliroadmap.backend.domain.dto.response.admin.AdminSystemHealthResponse;
import com.inteliroadmap.backend.domain.dto.response.admin.AdminUserListItemResponse;
import com.inteliroadmap.backend.domain.dto.response.admin.AdminUserMetricResponse;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.mappers.AdminDashboardMapper;
import com.inteliroadmap.backend.repositories.CareerRoleRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.security.JwtService;
import com.inteliroadmap.backend.utils.BearerTokenUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDashboardService {

    private final UserRepository userRepository;
    private final CareerRoleRepository careerRoleRepository;
    private final JwtService  jwtService;
    private final AdminDashboardMapper adminDashboardMapper;

    /**
     * Get total users metric for admin dashboard
     * */

    @Transactional
    public AdminUserMetricResponse getUserMetrics(String authorizationHeader) {
        validateAdmin(authorizationHeader);

        log.info("Admin Dashboard Module: Get user metric data");

        return adminDashboardMapper.toUserMetricResponse(userRepository.count(), 12);
    }

    /**
     * Get total learning paths/courses metric for admin dashboard
     * */

    @Transactional
    public AdminCourseMetricResponse getCourseMetrics(String authorizationHeader) {
        validateAdmin(authorizationHeader);

        log.info("Admin Dashboard Module: Get course metric data");

        long total = careerRoleRepository.count();

        return adminDashboardMapper.toCourseMetricResponse(total, "ACTIVE", 78);
    }

    /**
     * Get system health metric for admin dashboard.
     */
    public AdminSystemHealthResponse getSystemHealth(String authorizationHeader) {
        validateAdmin(authorizationHeader);

        log.info("Admin Dashboard Module: Get system health");

        return adminDashboardMapper.toSystemHealthResponse(99.9, "ONLINE");
    }

    /**
     * Get latest users for admin dashboard user table.
     */
    @Transactional
    public List<AdminUserListItemResponse> getUsers(String authorizationHeader) {
        validateAdmin(authorizationHeader);

        log.info("Admin Dashboard Module: Get users list");

        return userRepository.findAllUsers()
                .stream()
                .map(adminDashboardMapper::toUserListItem)
                .toList();
    }

    @Transactional
    public AdminUserListItemResponse updateUserRole(String authorizationHeader, String userId, UpdateUserRoleRequest request) {

        validateAdmin(authorizationHeader);

        log.info("Admin Dashboard Module: Update user role. userId: {}, role: {}", userId, request.getRole());

        User user = findUserById(userId);
        user.setRole(request.getRole());

        User updatedUser = userRepository.save(user);
        log.info("Admin Dashboard Module: User role updated successfully. email: {}, role: {}",
                updatedUser.getEmail(), updatedUser.getRole());

        return adminDashboardMapper.toUserListItem(updatedUser);
    }

    @Transactional
    public void deleteUser(String authorizationHeader, String userId) {validateAdmin(authorizationHeader);

        log.info("Admin Dashboard Module: Delete user. userId: {}", userId);

        String currentEmail = jwtService.extractEmail(BearerTokenUtil.extractToken(authorizationHeader));
        User user = findUserById(userId);

        if (user.getEmail().equals(currentEmail)) {
            throw new ResourceNotFoundException("Admin cannot delete own account");
        }

        userRepository.delete(user);

        log.info("Admin Dashboard Module: User deleted successfully. email: {}", user.getEmail());
    }

    private User findUserById(String userId) {
        try {
            UUID id = UUID.fromString(userId);
            Optional<User> userOptional = userRepository.findById(id);

            if (userOptional.isEmpty()) {
                throw new ResourceNotFoundException("User not found");
            }

            return userOptional.get();
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("Invalid user id");
        }
    }

    private void validateAdmin(String authorizationHeader) {
        String token = BearerTokenUtil.extractToken(authorizationHeader);
        String role = jwtService.extractRole(token);

        if (!jwtService.isTokenValid(token) || !"ADMIN".equals(role)) {
            log.warn("Admin Dashboard Module: Access denied. role: {}", role);
            throw new ResourceNotFoundException("Access denied");
        }
    }

}
