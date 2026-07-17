package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.LoginRequest;
import com.inteliroadmap.backend.domain.dto.response.auth.RefreshResponse;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

public interface AuthService {

    RefreshResponse login(LoginRequest request);

    RefreshResponse refreshAccount(String refreshToken);

    /**
     * @param expiresIn access-token expiry as an instant; it is serialised to ISO-8601 UTC so a
     *                  client in another timezone reads the same moment the server meant.
     */
    RefreshResponse refreshResponse(String accessToken, String refreshToken, Instant expiresIn);

    void logout(String refreshToken);

    ResponseStatusException invalidRefreshToken();
}
