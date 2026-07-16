package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.LoginRequest;
import com.inteliroadmap.backend.domain.dto.response.auth.RefreshResponse;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

public interface AuthService {

    RefreshResponse login(LoginRequest request);

    RefreshResponse refreshAccount(String refreshToken);

    RefreshResponse refreshResponse(String accessToken, String refreshToken, LocalDateTime expiresIn);

    void logout(String refreshToken);

    ResponseStatusException invalidRefreshToken();
}
