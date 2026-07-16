package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.response.auth.RefreshResponse;
import com.inteliroadmap.backend.domain.entity.RefreshToken;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.exceptions.UnauthorizedException;
import com.inteliroadmap.backend.repositories.RefreshTokenRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.security.JwtService;
import com.inteliroadmap.backend.security.TokenHashUtil;
import com.inteliroadmap.backend.services.AuthService;
import org.springframework.transaction.annotation.Transactional;
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

    // A just-rotated refresh token stays valid for this long instead of being deleted
    // immediately, so a concurrent or retried refresh carrying the same cookie (multiple
    // tabs, or many requests hitting a freshly-expired access token at once) still resolves
    // rather than forcing a logout. The old token self-expires after the window.
    private static final Duration ROTATION_GRACE = Duration.ofSeconds(30);

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

        // Step 4: Find and lock refresh token from database (stored as a SHA-256 digest)
        Optional<RefreshToken> storedTokenOptional =
                refreshTokenRepository.findByTokenForUpdate(TokenHashUtil.sha256Hex(refreshToken));

        if (storedTokenOptional.isEmpty()) {
            log.warn("AuthServiceImpl: Refresh token was not found for user: {}", email);
            throw new UnauthorizedException("Refresh token or user not found");
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
            throw new UnauthorizedException("Refresh token or user not found");
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
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresIn = now.plus(Duration.ofMillis(jwtService.getAccessExpiration()));

        // Step 7: Rotate. Keep the just-used token alive for a short grace window instead of
        // deleting it, so a concurrent/retried refresh with the same cookie still finds it and
        // succeeds. The old token becomes unusable once the window passes.
        storedToken.setExpiredAt(now.plus(ROTATION_GRACE));
        refreshTokenRepository.save(storedToken);

        RefreshToken newStoredToken = RefreshToken.builder()
                .token(TokenHashUtil.sha256Hex(newRefreshToken))
                .user(User.builder().userId(user.getUserId()).build())
                .expiredAt(now.plus(Duration.ofMillis(jwtService.getRefreshExpiration())))
                .build();

        refreshTokenRepository.save(newStoredToken);

        // Housekeeping: drop this user's tokens whose (grace-adjusted) expiry has already
        // passed so rotated stubs don't accumulate. Bulk delete skips the future-dated rows
        // we just wrote.
        refreshTokenRepository.deleteByUser_UserIdAndExpiredAtBefore(user.getUserId(), now);

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
        refreshTokenRepository.deleteByToken(TokenHashUtil.sha256Hex(refreshToken));
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
