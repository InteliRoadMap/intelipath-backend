package com.inteliroadmap.backend.security;

import com.inteliroadmap.backend.utils.CookieUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/** Handles the HTTP-only cookies used by authentication flows. */
@Component
@RequiredArgsConstructor
public class AuthenticationCookieService {

    public static final String ACCESS_TOKEN_COOKIE_NAME = "access_token";
    public static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
    // Scope covers both /api/v1/auth/refresh (rotation) and /api/v1/auth/logout (revocation)
    // so the browser sends the refresh cookie to both — otherwise logout cannot revoke it.
    public static final String REFRESH_TOKEN_COOKIE_PATH = "/api/v1/auth";

    private final JwtService jwtService;

    @Value("${app.security.cookie.secure:true}")
    private boolean secure;

    @Value("${app.security.cookie.same-site:Lax}")
    private String sameSite;

    public void addAuthenticationCookies(
            HttpServletResponse response,
            String accessToken,
            String refreshToken
    ) {
        addCookie(response, ACCESS_TOKEN_COOKIE_NAME, accessToken, jwtService.getAccessExpiration(), "/");
        addRefreshTokenCookie(response, refreshToken);
    }

    public void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        addCookie(response, REFRESH_TOKEN_COOKIE_NAME, refreshToken, jwtService.getRefreshExpiration(), REFRESH_TOKEN_COOKIE_PATH);
    }

    public void clearRefreshTokenCookie(HttpServletResponse response) {
        addCookie(response, REFRESH_TOKEN_COOKIE_NAME, "", 0, REFRESH_TOKEN_COOKIE_PATH);
    }

    public Optional<String> getAccessToken(HttpServletRequest request) {
        return CookieUtils.getCookie(request, ACCESS_TOKEN_COOKIE_NAME)
                .map(cookie -> cookie.getValue())
                .filter(value -> !value.isBlank());
    }

    public Optional<String> getRefreshToken(HttpServletRequest request) {
        return CookieUtils.getCookie(request, REFRESH_TOKEN_COOKIE_NAME)
                .map(cookie -> cookie.getValue())
                .filter(value -> !value.isBlank());
    }

    private void addCookie(
            HttpServletResponse response,
            String name,
            String value,
            long maxAgeMillis,
            String path
    ) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(path)
                .maxAge(Duration.ofMillis(maxAgeMillis))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
