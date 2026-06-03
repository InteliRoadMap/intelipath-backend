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

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;

    @Transactional
    public RefreshResponse refreshAccount(RefreshRequest refreshRequest) {
        log.info("Refresh access token");
        String refreshToken = refreshRequest.getRefreshToken();

        RefreshToken storedToken = refreshTokenRepository.findByToken(refreshToken);
        if (storedToken == null) {
            log.warn("Refresh token not found");
            throw new ResourceNotFoundException("Refresh token not found");
        }

        if (storedToken.getExpireAt().isBefore(LocalDateTime.now())) {
            log.warn("Refresh token expired");
            if (refreshTokenRepository.deleteByToken(refreshToken)) {
                log.warn("Refresh token deleted successfully");
            }
            throw new ResourceNotFoundException("Refresh token expired");
        }

        if (!jwtService.isTokenValid(refreshToken)) {
            log.warn("Refresh token invalid");
            throw new ResourceNotFoundException("Refresh token invalid");
        }

        String email = jwtService.extractEmail(refreshToken);
        User user = userRepository.findByEmail(email);
        if (user == null) {
            log.warn("Refresh Module: User not found: {}", email);
            throw new ResourceNotFoundException("User not found");
        }

        String newAccessToken = jwtService.generateAccessToken(
                user.getEmail(),
                user.getRole().name()
        );
        log.info("New access token generated for : {}", user.getFullName());
        LocalDateTime expiresIn = LocalDateTime.now().plus(Duration.ofMillis(jwtService.getAccessExpiration()));

        return refreshResponse(newAccessToken, expiresIn);
    }

    private RefreshResponse refreshResponse(String accessToken, LocalDateTime expiresIn) {
        log.info("Refresh access token");
        return RefreshResponse.builder()
                .accessToken(accessToken)
                .expiresIn(String.valueOf(expiresIn))
                .build();
    }
}
