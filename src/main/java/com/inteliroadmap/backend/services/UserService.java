package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.SetupUserProfileRequest;
import com.inteliroadmap.backend.domain.dto.request.UserRequest;
import com.inteliroadmap.backend.domain.dto.response.UserResponse;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.mappers.UserMapper;
import com.inteliroadmap.backend.repositories.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.time.LocalDate;

public interface UserService {

    public UserResponse getCurrentUser() ;

    public UserResponse getUserByEmail(UserRequest userRequest) ;

    public UserResponse setupUserProfile(SetupUserProfileRequest request) ;

    public UserResponse updateAvatar(org.springframework.web.multipart.MultipartFile file) ;
}
