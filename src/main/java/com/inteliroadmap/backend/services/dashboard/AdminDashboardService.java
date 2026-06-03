package com.inteliroadmap.backend.services.dashboard;

import com.inteliroadmap.backend.domain.dto.response.admin.AdminCourseMetricResponse;
import com.inteliroadmap.backend.domain.dto.response.admin.AdminSystemHealthResponse;
import com.inteliroadmap.backend.domain.dto.response.admin.AdminUserListItemResponse;
import com.inteliroadmap.backend.domain.dto.response.admin.AdminUserMetricResponse;
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

        return userRepository.findTop10ByOrderByEmailAsc()
                .stream()
                .map(adminDashboardMapper::toUserListItem)
                .toList();
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
