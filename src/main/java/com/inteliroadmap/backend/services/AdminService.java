package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.UpdateUserRoleRequest;
import com.inteliroadmap.backend.domain.dto.request.UpdateUserStatusRequest;
import com.inteliroadmap.backend.domain.dto.response.admin.AdminCourseMetricResponse;
import com.inteliroadmap.backend.domain.dto.response.admin.AdminSystemHealthResponse;
import com.inteliroadmap.backend.domain.dto.response.admin.AdminUserListItemResponse;
import com.inteliroadmap.backend.domain.dto.response.admin.AdminUserMetricResponse;

import java.util.List;

public interface AdminService {

    AdminUserMetricResponse getUserMetrics();

    AdminCourseMetricResponse getCourseMetrics();

    AdminSystemHealthResponse getSystemHealth();

    List<AdminUserListItemResponse> getUsers();

    AdminUserListItemResponse updateUserRole(String userId, UpdateUserRoleRequest request);

    AdminUserListItemResponse updateUserStatus(String userId, UpdateUserStatusRequest request);

    void deleteUser(String userId);
}
