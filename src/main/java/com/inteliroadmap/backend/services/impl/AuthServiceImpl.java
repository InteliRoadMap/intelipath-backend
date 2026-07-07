package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.response.auth.RefreshResponse;
import com.inteliroadmap.backend.domain.entity.RefreshToken;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.repositories.RefreshTokenRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.security.JwtService;
import com.inteliroadmap.backend.services.AuthService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Implementation of AuthService for handling user authentication and token lifecycle.
 * Manages access and refresh tokens validation, rotation, and revocation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;

    /**
     * Refresh access token and rotate refresh token.
     *
     * @param refreshToken refresh token read from HttpOnly cookie
     * @return RefreshResponse containing new access token, refresh token and expiration time
     */
    @Transactional
    @Override
    public RefreshResponse refreshAccount(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            log.warn("AuthServiceImpl: Refresh token cookie is missing");
            throw invalidRefreshToken();
        }
        // Step 2: Validate JWT signature and expiration
        if (!jwtService.isTokenValid(refreshToken)) {
            log.warn("AuthServiceImpl: Refresh token validation failed");
            throw invalidRefreshToken();
        }

        // Step 3: Extract user email from refresh token subject
        String email = jwtService.extractEmail(refreshToken);
        if (email == null || email.isBlank()) {
            log.warn("AuthServiceImpl: Refresh token subject is missing");
            throw invalidRefreshToken();
        }

        // Step 4: Find and lock refresh token from database
        Optional<RefreshToken> storedTokenOptional =
                refreshTokenRepository.findByTokenForUpdate(refreshToken);

        if (storedTokenOptional.isEmpty()) {
            log.warn("AuthServiceImpl: Refresh token was not found for user: {}", email);
            throw new ResourceNotFoundException("Refresh token or user not found");
        }

        RefreshToken storedToken = storedTokenOptional.get();
        if (storedToken.getExpiredAt().isBefore(LocalDateTime.now())) {
            log.warn("AuthServiceImpl: Stored refresh token has expired for user: {}", email);
            throw invalidRefreshToken();
        }

        // Step 5: Find user by email and verify token ownership
        User user = userRepository.findByEmail(email);
        if (user == null) {
            log.warn("AuthServiceImpl: Refresh token user was not found: {}", email);
            throw new ResourceNotFoundException("Refresh token or user not found");
        }

        if (!storedToken.getUser().getUserId().equals(user.getUserId())) {
            log.warn("AuthServiceImpl: Refresh token ownership mismatch for user: {}", email);
            throw invalidRefreshToken();
        }

        // Step 6: Generate new access token and refresh token
        String newAccessToken = jwtService.generateAccessToken(
                user.getEmail(),
                user.getRole().name()
        );
        String newRefreshToken = jwtService.generateRefreshToken(user.getEmail());
        LocalDateTime expiresIn = LocalDateTime.now().plus(Duration.ofMillis(jwtService.getAccessExpiration()));

        // Step 7: Delete old refresh token and save new refresh token
        refreshTokenRepository.delete(storedToken);

        RefreshToken newStoredToken = RefreshToken.builder()
                .token(newRefreshToken)
                .user(User.builder().userId(user.getUserId()).build())
                .expiredAt(LocalDateTime.now().plus(Duration.ofMillis(jwtService.getRefreshExpiration())))
                .build();

        refreshTokenRepository.save(newStoredToken);

        // Step 8: Return new token pair
        log.info("AuthServiceImpl: Refresh token rotated successfully for user: {}", email);
        return refreshResponse(newAccessToken, newRefreshToken, expiresIn);
    }

    @Transactional
    @Override
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.deleteByToken(refreshToken);
    }

    /**
     * Build refresh token response.
     *
     * @param accessToken new access token
     * @param refreshToken new refresh token
     * @param expiresIn access token expiration time
     * @return RefreshResponse containing generated token information
     */
    @Override
    public RefreshResponse refreshResponse(String accessToken, String refreshToken, LocalDateTime expiresIn) {
        // Construct and return a structured response containing the new tokens and expiration time
        return RefreshResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(String.valueOf(expiresIn))
                .build();
    }

    /**
     * Create unauthorized exception for invalid or expired refresh token.
     *
     * @return ResponseStatusException with HTTP 401 status
     */
    @Override
    public ResponseStatusException invalidRefreshToken() {
        // Generate a 401 Unauthorized exception for any refresh token failures
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Refresh token is invalid or expired"
        );
    }
}
