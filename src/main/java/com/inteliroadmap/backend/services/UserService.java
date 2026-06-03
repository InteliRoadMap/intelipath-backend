package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.UserRequest;
import com.inteliroadmap.backend.domain.dto.response.UserResponse;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.mappers.UserMapper;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.security.JwtService;
import com.inteliroadmap.backend.utils.BearerTokenUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    /**
     * Get current authenticated user information from JWT access token.
     *
     * @param authorizationHeader Authorization header containing Bearer access token
     * @return UserResponse containing current authenticated user information
     * @throws ResourceNotFoundException if token is missing, invalid, or user not found
     */
    @Transactional
    public UserResponse getCurrentUser(String authorizationHeader) {
        log.info("User Module: Current user info request received");

        //B1: Extract access token from Authorization header
        String accessToken = BearerTokenUtil.extractToken(authorizationHeader);

        //B2: Validate access token
        if (!jwtService.isTokenValid(accessToken)) {
            log.warn("User Module: Invalid or expired access token");
            throw new ResourceNotFoundException("Invalid or expired access token");
        }

        //B3: Extract email from access token
        String email = jwtService.extractEmail(accessToken);
        if (email == null || email.isBlank()) {
            log.warn("User Module: Cannot extract email from access token");
            throw new ResourceNotFoundException("Cannot extract email from access token");
        }

        //B4: Find user by email
        User user = userRepository.findByEmail(email);
        if (user == null) {
            log.warn("User Module: User not found: {}", email);
            throw new ResourceNotFoundException("User not found");
        }

        //B5: Build user response
        return userMapper.toUserResponse(user);
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
        return userMapper.toUserResponse(user);
    }
}
