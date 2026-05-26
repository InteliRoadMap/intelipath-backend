package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.LoginRequest;
import com.inteliroadmap.backend.domain.dto.request.RegisterRequest;
import com.inteliroadmap.backend.domain.dto.response.RegisterResponse;
import com.inteliroadmap.backend.domain.dto.response.UserResponse;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.domain.enums.UserRole;
import com.inteliroadmap.backend.domain.enums.UserStatus;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.security.JwtService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service - Authentication Service
 *
 * Handles all authentication-related business logic:
 * - Register new student account
 * - Login with email and password
 *
 * @author InteliPath Team
 * @version 2026.0524
 * @since 2026
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    /**
     * Repository and utility dependencies (auto-injected by Spring)
     */
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * Register new student account
     *
     * @param registerRequest RegisterRequest containing email, password, fullName
     * @return UserResponse containing JWT token and user info
     * @throws ResourceNotFoundException if email already exists
     */

    @Transactional
    public RegisterResponse registerAccount(RegisterRequest registerRequest) {
        log.info("Register request received for email: {}", registerRequest.getEmail());

        //B1: Check duplicate email registration
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            log.warn("Email already in use: {}", registerRequest.getEmail());
            throw new ResourceNotFoundException("Email already in use");
        }

        //B2: Build User entity from request
        User user = buildUser(registerRequest);
        userRepository.save(user);
        log.info("User registered successfully: {}", registerRequest.getEmail());

        return RegisterResponse.builder()
                .message("Welcome to InteliPath," + user.getFullName())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .build();
    }

    /**
     * Authenticate user using email and password
     *
     * Validation:
     * 1. Verify email exists in database
     * 2. Verify password matches encoded password
     * 3. Verify account is not suspended
     *
     * @param loginRequest LoginRequest containing email and password
     * @return UserResponse containing JWT token and user info
     * @throws ResourceNotFoundException if email not found, wrong password, or account suspended
     */
    @Transactional
    public UserResponse loginAccount(LoginRequest loginRequest) {
        log.info("Login request received for email: {}", loginRequest.getEmail());

        //B1: Find user bt email
        User user = userRepository.findByEmail(loginRequest.getEmail());
        if  (user == null) {
            log.warn("User not found: {}", loginRequest.getEmail());
            throw new ResourceNotFoundException("User not found");
        }

        //B2: Verify password against BCrypt encoded
        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            log.warn("Passwords don't match");
            throw new ResourceNotFoundException("Passwords don't match");
        }

        //B3: Prevent suspended account
        if (user.getUserStatus() == UserStatus.SUSPENDED) {
            log.warn("User is Suspended");
            throw new ResourceNotFoundException("User is Suspended");
        }

        log.info("User logged in successfully: {}", loginRequest.getEmail());
        return buildAuthResponse(user);

    }


    /**
     * Build UserResponse DTO from authenticated User entity
     *
     * Generates JWT access token and maps user fields to response
     *
     * @param user Authenticated User entity
     * @return UserResponse containing JWT token and user info
     */
    public UserResponse buildAuthResponse (User user) {
        log.info("Build Auth Response for email: {}", user.getEmail());
        return UserResponse.builder()
                .accessToken(jwtService.generateToken(
                        user.getEmail(),
                        String.valueOf(user.getRole())
                        )
                )
                .userId(user.getUserId().toString())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .build();
    }


    /**
     * Build new User entity from RegisterRequest
     *
     * Password is encoded using BCrypt before persisting
     *
     * @param request RegisterRequest payload
     * @return User entity ready to be persisted
     */
   private User buildUser(RegisterRequest registerRequest) {
        log.debug("Build User with email: {}", registerRequest.getEmail());
        return User.builder()
                .email(registerRequest.getEmail())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .fullName(registerRequest.getFullName())
                .role(UserRole.STUDENT)
                .build();
   }




}