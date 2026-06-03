package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.*;
import com.inteliroadmap.backend.domain.dto.response.RefreshResponse;
import com.inteliroadmap.backend.domain.dto.response.UserResponse;
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
//                .accessToken(
//                        jwtService.generateAccessToken(
//                                user.getEmail(),
//                                user.getRole().name()
//                        )
//                )
//                .refreshToken(refreshToken)
//                .expiresIn(String.valueOf(expiresIn))
                .id(user.getUserId().toString())
                .fullName(user.getFullName())
                .role(user.getRole().name())
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