package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.RefreshRequest;
import com.inteliroadmap.backend.domain.dto.response.RefreshResponse;
import com.inteliroadmap.backend.domain.entity.RefreshToken;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.repositories.RefreshTokenRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.security.JwtService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;

    /**
     * Refresh access token and rotate refresh token.
     *
     * @param refreshRequest request body containing refresh token
     * @return RefreshResponse containing new access token, refresh token and expiration time
     */
    @Transactional
    public RefreshResponse refreshAccount(RefreshRequest refreshRequest) {
        // Step 1: Get refresh token from request body
        String refreshToken = refreshRequest.getRefreshToken();

        // Step 2: Validate JWT signature and expiration
        if (!jwtService.isTokenValid(refreshToken)) {
            log.warn("Refresh token validation failed");
            throw invalidRefreshToken();
        }

        // Step 3: Extract user email from refresh token subject
        String email = jwtService.extractEmail(refreshToken);
        if (email == null || email.isBlank()) {
            log.warn("Refresh token subject is missing");
            throw invalidRefreshToken();
        }

        // Step 4: Find and lock refresh token from database
        Optional<RefreshToken> storedTokenOptional =
                refreshTokenRepository.findByTokenForUpdate(refreshToken);

        if (storedTokenOptional.isEmpty()) {
            log.warn("Refresh token was not found for user: {}", email);
            throw new ResourceNotFoundException("Refresh token or user not found");
        }

        RefreshToken storedToken = storedTokenOptional.get();
        if (storedToken.getExpireAt().isBefore(LocalDateTime.now())) {
            log.warn("Stored refresh token has expired for user: {}", email);
            throw invalidRefreshToken();
        }

        // Step 5: Find user by email and verify token ownership
        User user = userRepository.findByEmail(email);
        if (user == null) {
            log.warn("Refresh token user was not found: {}", email);
            throw new ResourceNotFoundException("Refresh token or user not found");
        }

        if (!storedToken.getUser().getUserId().equals(user.getUserId())) {
            log.warn("Refresh token ownership mismatch for user: {}", email);
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
                .user(user)
                .expireAt(LocalDateTime.now().plus(Duration.ofMillis(jwtService.getRefreshExpiration())))
                .build();

        refreshTokenRepository.save(newStoredToken);

        // Step 8: Return new token pair
        log.info("Refresh token rotated successfully for user: {}", email);
        return refreshResponse(newAccessToken, newRefreshToken, expiresIn);
    }

    /**
     * Build refresh token response.
     *
     * @param accessToken new access token
     * @param refreshToken new refresh token
     * @param expiresIn access token expiration time
     * @return RefreshResponse containing generated token information
     */
    private RefreshResponse refreshResponse(String accessToken, String refreshToken, LocalDateTime expiresIn) {
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
    private ResponseStatusException invalidRefreshToken() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Refresh token is invalid or expired"
        );
    }

    /**
     * Rotate refresh token for OAuth2 login
     * @param user user entity
     * @param refreshToken new refresh token string
     */
    @Transactional
    public void rotateRefreshTokenForOAuth2User(User user, String refreshToken) {
        refreshTokenRepository.deleteByUser_UserId(user.getUserId());
        RefreshToken token = RefreshToken.builder()
                .token(refreshToken)
                .user(user)
                .expireAt(LocalDateTime.now().plus(Duration.ofMillis(jwtService.getRefreshExpiration())))
                .build();
        refreshTokenRepository.save(token);
    }
}
