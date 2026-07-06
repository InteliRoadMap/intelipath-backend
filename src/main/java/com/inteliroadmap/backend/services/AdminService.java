package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.UpdateUserRoleRequest;
import com.inteliroadmap.backend.domain.dto.response.admin.AdminCourseMetricResponse;
import com.inteliroadmap.backend.domain.dto.response.admin.AdminSystemHealthResponse;
import com.inteliroadmap.backend.domain.dto.response.admin.AdminUserListItemResponse;
import com.inteliroadmap.backend.domain.dto.response.admin.AdminUserMetricResponse;

import java.util.List;

public interface AdminService {

    AdminUserMetricResponse getUserMetrics(String authorizationHeader) ;

    AdminCourseMetricResponse getCourseMetrics(String authorizationHeader) ;

    AdminSystemHealthResponse getSystemHealth(String authorizationHeader) ;

    List<AdminUserListItemResponse> getUsers(String authorizationHeader) ;

    AdminUserListItemResponse updateUserRole(String authorizationHeader, String userId, UpdateUserRoleRequest request) ;

    void deleteUser(String authorizationHeader, String userId) ;
}
