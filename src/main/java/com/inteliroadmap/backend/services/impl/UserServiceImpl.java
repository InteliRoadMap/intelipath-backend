package com.inteliroadmap.backend.services.impl;

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

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final SupabaseStorageServiceImpl supabaseStorageService;

    @Transactional
    public UserResponse getCurrentUser() {
        log.info("User Module: Current user info request received");

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        if (email == null || email.isBlank()) {
            log.warn("User Module: Cannot extract email from security context");
            throw new ResourceNotFoundException("Cannot extract email from security context");
        }

        User user = userRepository.findByEmail(email);
        if (user == null) {
            log.warn("User Module: User not found: {}", email);
            throw new ResourceNotFoundException("User not found");
        }

        return userMapper.toUserResponse(user);
    }

    @Transactional
    public UserResponse getUserByEmail(UserRequest userRequest) {
        log.info("User Module: User info request received for email: {}", userRequest.getEmail());

        User user = userRepository.findByEmail(userRequest.getEmail());
        if (user == null) {
            log.warn("User Module: User not found: {}", userRequest.getEmail());
            throw new ResourceNotFoundException("User not found");
        }

        return userMapper.toUserResponse(user);
    }

    @Transactional
    public UserResponse setupUserProfile(SetupUserProfileRequest request) {
        log.info("User Module: Setup user profile request received");

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new ResourceNotFoundException("User not found");
        }

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getYob() != null && !request.getYob().trim().isEmpty()) {
            user.setYob(LocalDate.parse(request.getYob()));
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }

        userRepository.save(user);

        return userMapper.toUserResponse(user);
    }

    @Transactional
    public UserResponse updateAvatar(org.springframework.web.multipart.MultipartFile file) {
        log.info("User Module: Update avatar request received");

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new ResourceNotFoundException("User not found");
        }

        String publicUrl = supabaseStorageService.uploadAvatar(file, user.getUserId().toString());
        user.setAvatarUrl(publicUrl);
        userRepository.save(user);

        return userMapper.toUserResponse(user);
    }
}
