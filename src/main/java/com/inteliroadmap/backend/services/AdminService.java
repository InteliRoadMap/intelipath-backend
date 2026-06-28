package com.inteliroadmap.backend.services;

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
import com.inteliroadmap.backend.utils.BearerTokenUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminService {

    public AdminUserMetricResponse getUserMetrics(String authorizationHeader) ;

    public AdminCourseMetricResponse getCourseMetrics(String authorizationHeader) ;

    public AdminSystemHealthResponse getSystemHealth(String authorizationHeader) ;

    public List<AdminUserListItemResponse> getUsers(String authorizationHeader) ;

    public AdminUserListItemResponse updateUserRole(String authorizationHeader, String userId, UpdateUserRoleRequest request) ;

    public void deleteUser(String authorizationHeader, String userId) ;
}
