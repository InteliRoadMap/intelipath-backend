package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.SetupUserProfileRequest;
import com.inteliroadmap.backend.domain.dto.request.UserRequest;
import com.inteliroadmap.backend.domain.dto.response.UserResponse;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.security.JwtService;
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
    private final JwtService jwtService;

    /**
     * Get current authenticated user information from JWT access token.
     *
     * @param authorizationHeader Authorization header containing Bearer access token
     * @return UserResponse containing current authenticated user information
     * @throws ResourceNotFoundException if token is missing, invalid, or user not found
     */
    @Transactional
    public UserResponse getCurrentUser() {
        log.info("User Module: Current user info request received");

        //B1: Extract email from SecurityContextHolder (populated by JwtAuthenticationFilter)
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        if (email == null || email.isBlank()) {
            log.warn("User Module: Cannot extract email from security context");
            throw new ResourceNotFoundException("Cannot extract email from security context");
        }

        //B2: Find user by email
        User user = userRepository.findByEmail(email);
        if (user == null) {
            log.warn("User Module: User not found: {}", email);
            throw new ResourceNotFoundException("User not found");
        }

        //B3: Build user response
        return buildUserResponse(user);
    }

    /**
     * Get user information by email.
     *
     * @param userRequest UserRequest containing email
     * @return UserResponse containing user information
     * @throws ResourceNotFoundException if user not found
     */
    @Transactional
    public UserResponse getUserByEmail(UserRequest userRequest) {
        log.info("User Module: User info request received for email: {}", userRequest.getEmail());

        //B1: Find user by email
        User user = userRepository.findByEmail(userRequest.getEmail());
        if (user == null) {
            log.warn("User Module: User not found: {}", userRequest.getEmail());
            throw new ResourceNotFoundException("User not found");
        }

        //B2: Build user response
        return buildUserResponse(user);
    }

    @Transactional
    public UserResponse setupUserProfile(SetupUserProfileRequest request) {
        log.info("User Module: Setup user profile request received");
        
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new ResourceNotFoundException("User not found");
        }
        
        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getYob() != null) {
            if (request.getYob().trim().isEmpty()) {
                user.setYob(null);
            } else {
                user.setYob(LocalDate.parse(request.getYob()));
            }
        }
        if (request.getBio() != null) user.setBio(request.getBio());
        
        userRepository.save(user);
        
        return buildUserResponse(user);
    }

    /**
     * Build UserResponse DTO from User entity.
     *
     * @param user User entity
     * @return UserResponse containing user information
     */
    private UserResponse buildUserResponse(User user) {
        log.info("User Module: Build UserResponse for email: {}", user.getEmail());

        return UserResponse.builder()
                .id(user.getUserId().toString())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .build();
    }
}