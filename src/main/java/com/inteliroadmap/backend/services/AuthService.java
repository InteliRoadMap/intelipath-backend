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

    /**
     * Starts the magic-link password reset. Always completes without revealing whether the
     * email maps to an account: if it does, a single-use token is minted, hashed, stored, and
     * emailed as a link; if it doesn't, nothing happens. The caller returns a neutral 200 either way.
     */
    void forgotPassword(String email);

    /**
     * Completes a reset: validates the raw token against its stored digest (unused, unexpired),
     * sets the new BCrypt password, and marks the token used. Throws 400 on any invalid token.
     */
    void resetPassword(String token, String newPassword);

    ResponseStatusException invalidRefreshToken();
}
