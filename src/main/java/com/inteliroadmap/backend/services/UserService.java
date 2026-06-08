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

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

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
        if (request.getYob() != null) {
            if (request.getYob().trim().isEmpty()) {
                user.setYob(null);
            } else {
                user.setYob(LocalDate.parse(request.getYob()));
            }
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }

        userRepository.save(user);

        return userMapper.toUserResponse(user);
    }
}
