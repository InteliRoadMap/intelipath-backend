package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.response.RefreshResponse;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

public interface AuthService {

    RefreshResponse refreshAccount(String refreshToken);

    RefreshResponse refreshResponse(String accessToken, String refreshToken, LocalDateTime expiresIn);

    void logout(String refreshToken);

    ResponseStatusException invalidRefreshToken();
}
