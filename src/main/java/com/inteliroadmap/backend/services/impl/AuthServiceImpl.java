package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.request.LoginRequest;
import com.inteliroadmap.backend.domain.dto.response.auth.RefreshResponse;
import com.inteliroadmap.backend.domain.entity.PasswordResetToken;
import com.inteliroadmap.backend.domain.entity.RefreshToken;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.domain.enums.UserStatus;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.exceptions.UnauthorizedException;
import com.inteliroadmap.backend.repositories.PasswordResetTokenRepository;
import com.inteliroadmap.backend.repositories.RefreshTokenRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.security.JwtService;
import com.inteliroadmap.backend.security.TokenHashUtil;
import com.inteliroadmap.backend.services.AuthService;
import com.inteliroadmap.backend.services.EmailService;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
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

    // A reset link is valid for 30 minutes: long enough to survive an email-scanner
    // prefetch and the user switching devices, short enough that a leaked link expires soon.
    private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(30);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    // Base of the emailed reset link; the frontend serves /reset-password?token=...
    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    /**
     * Authenticates a provisioned account (staff or FPT student) by username and password.
     *
     * Unlike the OAuth path this never creates a user: provisioning is the only way an
     * account with a local credential comes into existence.
     *
     * @param request the submitted username and password
     * @return RefreshResponse containing a new access token and refresh token
     */
    @Transactional
    @Override
    public RefreshResponse login(LoginRequest request) {
        log.info("AuthServiceImpl: Login request received");
        // Any role may hold a local credential; the account's own username decides who
        // this is, never the caller.
        User user = userRepository.findByUsername(request.getUsername()).orElse(null);

        // One message and one code for every failure, so a caller cannot tell an unknown
        // username from a wrong password, nor discover which accounts have local credentials.
        if (user == null
                || user.getPasswordHash() == null
                || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("AuthServiceImpl: Login failed for username: {}", request.getUsername());
            throw invalidCredentials();
        }

        if (user.getUserStatus() != null && user.getUserStatus() != UserStatus.ACTIVE) {
            log.warn("AuthServiceImpl: Login rejected, account is {}: {}", user.getUserStatus(), user.getEmail());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is not active");
        }

        log.info("AuthServiceImpl: Login successful for user: {}", user.getEmail());
        return issueTokens(user);
    }

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

        // Step 6: Rotate. Keep the just-used token alive for a short grace window instead of
        // deleting it, so a concurrent/retried refresh with the same cookie still finds it and
        // succeeds. The old token becomes unusable once the window passes.
        storedToken.setExpiredAt(LocalDateTime.now().plus(ROTATION_GRACE));
        refreshTokenRepository.save(storedToken);

        // Step 7: Issue and persist a fresh pair
        log.info("AuthServiceImpl: Refresh token rotated successfully for user: {}", email);
        return issueTokens(user);
    }

    /**
     * Mints an access/refresh pair for a user and persists the hashed refresh token.
     * Shared by login and refresh so the two paths cannot drift apart.
     *
     * @param user the authenticated user
     * @return RefreshResponse containing the new tokens and access-token expiry
     */
    private RefreshResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user.getEmail(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());
        LocalDateTime now = LocalDateTime.now();
        // Instant, not LocalDateTime: expiresIn crosses the wire to a browser in another
        // timezone, and a zone-less timestamp is read there as local time. The server runs
        // UTC and clients run UTC+7, so that mis-read lands ~7h in the past and the client
        // refreshes on a hot loop until it gives up and logs the user out.
        Instant expiresIn = Instant.now().plusMillis(jwtService.getAccessExpiration());

        RefreshToken storedToken = RefreshToken.builder()
                .token(TokenHashUtil.sha256Hex(refreshToken))
                .user(User.builder().userId(user.getUserId()).build())
                .expiredAt(now.plus(Duration.ofMillis(jwtService.getRefreshExpiration())))
                .build();
        refreshTokenRepository.save(storedToken);

        // Housekeeping: drop this user's tokens whose (grace-adjusted) expiry has already
        // passed so rotated stubs don't accumulate. Bulk delete skips the future-dated rows
        // we just wrote.
        refreshTokenRepository.deleteByUser_UserIdAndExpiredAtBefore(user.getUserId(), now);

        return refreshResponse(accessToken, refreshToken, expiresIn);
    }

    /**
     * Create unauthorized exception for a failed username/password login.
     *
     * @return ResponseStatusException with HTTP 401 status
     */
    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }

    @Transactional
    @Override
    public void forgotPassword(String email) {
        log.info("AuthServiceImpl: Password reset requested");
        User user = userRepository.findByEmail(email);

        // Neutral outcome: never reveal whether the address exists, and skip OAuth-only
        // accounts that have no local password to reset. The controller returns 200 regardless.
        if (user == null || user.getPasswordHash() == null) {
            log.info("AuthServiceImpl: Reset requested for an address with no local credential; no email sent");
            return;
        }

        // One pending link per user: drop older tokens so a previously emailed link
        // can't be redeemed after this fresh request.
        passwordResetTokenRepository.deleteByUser_UserId(user.getUserId());

        String rawToken = generateResetToken();
        LocalDateTime now = LocalDateTime.now();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(User.builder().userId(user.getUserId()).build())
                .tokenHash(TokenHashUtil.sha256Hex(rawToken))
                .expiresAt(now.plus(RESET_TOKEN_TTL))
                .createdAt(now)
                .build();
        passwordResetTokenRepository.save(resetToken);

        String resetLink = UriComponentsBuilder.fromUriString(frontendUrl)
                .path("/reset-password")
                .queryParam("token", rawToken)
                .build()
                .toUriString();
        emailService.sendPasswordResetEmail(user.getEmail(), user.getFullName(), resetLink);
        log.info("AuthServiceImpl: Password reset link sent for user: {}", user.getEmail());
    }

    @Transactional
    @Override
    public void resetPassword(String token, String newPassword) {
        log.info("AuthServiceImpl: Password reset submission received");

        // Locked lookup so two concurrent redemptions of the same link cannot both pass the
        // used_at check.
        PasswordResetToken stored = passwordResetTokenRepository
                .findByTokenHashForUpdate(TokenHashUtil.sha256Hex(token))
                .orElseThrow(this::invalidResetToken);

        if (stored.getUsedAt() != null || stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("AuthServiceImpl: Reset token already used or expired");
            throw invalidResetToken();
        }

        User user = userRepository.findByUserId(stored.getUser().getUserId());
        if (user == null) {
            throw invalidResetToken();
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Single-use: burn the token so the link cannot be replayed.
        stored.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(stored);

        // A password change should end existing sessions on other devices.
        refreshTokenRepository.deleteByUser_UserId(user.getUserId());
        log.info("AuthServiceImpl: Password reset successful for user: {}", user.getEmail());
    }

    /** High-entropy URL-safe token; only its SHA-256 digest is persisted. */
    private String generateResetToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** One code and message for every reset failure, so a caller cannot probe token state. */
    private ResponseStatusException invalidResetToken() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reset link is invalid or has expired");
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
    public RefreshResponse refreshResponse(String accessToken, String refreshToken, Instant expiresIn) {
        // Instant.toString() is ISO-8601 UTC ("...Z"), so the client parses the moment the
        // server meant rather than the same wall clock in its own timezone.
        return RefreshResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(expiresIn.toString())
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
