package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.UpdateUserRoleRequest;
import com.inteliroadmap.backend.domain.dto.request.UpdateUserStatusRequest;
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
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminService {

    AdminUserMetricResponse getUserMetrics();

    AdminCourseMetricResponse getCourseMetrics();

    AdminSystemHealthResponse getSystemHealth();

    List<AdminUserListItemResponse> getUsers();

    AdminUserListItemResponse updateUserRole(String userId, UpdateUserRoleRequest request);

    AdminUserListItemResponse updateUserStatus(String userId, UpdateUserStatusRequest request);

    void deleteUser(String userId);
}
