package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.*;
import com.inteliroadmap.backend.domain.dto.response.RefreshResponse;
import com.inteliroadmap.backend.domain.dto.response.RegisterResponse;
import com.inteliroadmap.backend.domain.dto.response.UserResponse;
import com.inteliroadmap.backend.domain.entity.RefreshToken;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.domain.enums.UserRole;
import com.inteliroadmap.backend.domain.enums.UserStatus;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.repositories.RefreshTokenRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.security.JwtService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
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
        log.info("Register Module: Register request received for user: {}", registerRequest.getUsername());

        //B1: Check duplicate email registration
        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            log.warn("Register Module: Username already in use: {}", registerRequest.getUsername());
            throw new ResourceNotFoundException("Username already in use");
        }

        //B2: Build User entity from request
        User user = buildUser(registerRequest);
        userRepository.save(user);
        log.info("Register Module: User registered successfully: {}", registerRequest.getEmail());

        return RegisterResponse.builder()
                .message("Welcome to InteliPath, " + user.getUsername())
                .email(user.getEmail())
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
        log.info("Login Module: Login request received for user: {}", loginRequest.getUsernameOrEmail());

        //B1: Find user by username or email
        User user = userRepository.findByEmail(loginRequest.getUsernameOrEmail());
        if (user == null) {
            user = userRepository.findByUsername(loginRequest.getUsernameOrEmail());
            if (user == null) {
                log.warn("Login Module: User not found: {}", loginRequest.getUsernameOrEmail());
                throw new ResourceNotFoundException("User not found");
            }
        }

         //B2: Verify password against BCrypt encoded
         if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
             log.warn("Login Module: Passwords don't match");
             throw new ResourceNotFoundException("Passwords don't match");
         }

        //B3: Prevent suspended account
        if (user.getUserStatus() == UserStatus.SUSPENDED) {
            log.warn("Login Module: User is Suspended");
            throw new ResourceNotFoundException("User is Suspended");
        }

        log.info("Login Module: User prepare to create Refresh token");
        LocalDateTime accessExpiresIn = LocalDateTime.now()
                .plus(Duration.ofMillis(jwtService.getAccessExpiration()));

        String refreshToken = createAndSaveRefreshToken(user);
        return buildAuthResponse(user, refreshToken, accessExpiresIn);

    }

    @Transactional
    public RefreshResponse  refreshAccount(RefreshRequest refreshRequest) {
        log.info("Refresh access token");
        String refreshToken = refreshRequest.getRefreshToken();
        //B1: Check refresh token exists in DB
        RefreshToken storedToken = refreshTokenRepository.findByToken(refreshToken);
        if (storedToken == null) {
            log.warn("Refresh token not found");
            throw new ResourceNotFoundException("Refresh token not found");
        }

        //B2: Check expired in DB
        if (storedToken.getExpireAt().isBefore(LocalDateTime.now())){
            log.warn("Refresh token expired");
            if (refreshTokenRepository.deleteByToken(refreshToken)){
                log.warn("Refresh token deleted successfully");
            }
            throw new ResourceNotFoundException("Refresh token expired");
        }

        //B3: Check JWT token is valid
        if (!jwtService.isTokenValid(refreshToken)) {
            log.warn("Refresh token invalid");
            throw new ResourceNotFoundException("Refresh token invalid");
        }

        //B4: Check user from refresh token
        String email = jwtService.extractEmail(refreshToken);

        User user = userRepository.findByEmail(email);
        if (user == null) {
            log.warn("Refresh Module: User not found: {}", email);
            throw new ResourceNotFoundException("User not found");
        }

        //B5: Generate new access token
        String newAccessToken = jwtService.generateAccessToken(
                user.getEmail(),
                user.getRole().name()
        );
        log.info("New access token generated for : {}", user.getFullName());
        LocalDateTime expiresIn = LocalDateTime.now().plus(Duration.ofMillis(jwtService.getAccessExpiration()));

        return refreshResponse(newAccessToken, expiresIn);

    }

    /**
     * Build UserResponse DTO from authenticated User entity
     * @param user Authenticated User entity
     * @return UserResponse containing JWT token and user info
     */
    public UserResponse buildAuthResponse(User user, String refreshToken, LocalDateTime expiresIn) {
        log.info("Build Auth Response for email: {}", user.getEmail());
        return UserResponse.builder()
                .accessToken(
                        jwtService.generateAccessToken(
                                user.getEmail(),
                                user.getRole().name()
                        )
                )
                .refreshToken(refreshToken)
                .expiresIn(String.valueOf(expiresIn))
                .id(user.getUserId().toString())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .build();
    }

    /**
     * Build new User entity from RegisterRequest
     * @param registerRequest RegisterRequest payload
     * @return User entity ready to be persisted
     */
    private User buildUser(RegisterRequest registerRequest) {
         log.debug("Build User: {}", registerRequest.getUsername());
         return User.builder()
                 .username(registerRequest.getUsername())
                 .email(registerRequest.getEmail())
                 .password(passwordEncoder.encode(registerRequest.getPassword()))
                 .role(UserRole.STUDENT)
                 .build();
    }

   private RefreshResponse refreshResponse(String accessToken, LocalDateTime expiresIn) {
        log.info("Refresh access token");
        return RefreshResponse.builder()
                .accessToken(accessToken)
                .expiresIn(String.valueOf(expiresIn))
                .build();

   }

    private String createAndSaveRefreshToken(User user) {
        log.info("Create and Save Refresh token for user: {}", user.getEmail());
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());
        RefreshToken token = RefreshToken.builder()
                .token(refreshToken)
                .user(user)
                .expireAt(
                        LocalDateTime.now()
                                .plus(Duration.ofMillis(jwtService.getRefreshExpiration()))
                )
                .build();
        refreshTokenRepository.save(token);
        return refreshToken;
    }
}