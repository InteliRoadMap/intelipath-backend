package com.inteliroadmap.backend.security;

import com.inteliroadmap.backend.utils.CookieUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class HttpCookieOAuth2AuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
    public static final String OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME = "oauth2_auth_request";
    private static final int cookieExpireSeconds = 180;

    private final SecureOAuth2CookieCodec cookieCodec;

    @Value("${app.security.cookie.secure:false}")
    private boolean secure;

    @Value("${app.security.cookie.same-site:Lax}")
    private String sameSite;

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        var authorizationCookie = CookieUtils.getCookie(request, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
        if (authorizationCookie.isEmpty()) {
            log.warn("OAuth authorization request cookie is missing on callback. "
                    + "Start OAuth with a browser navigation (not fetch/Axios), use the same backend host for "
                    + "the authorization URL and callback URL, and verify COOKIE_SECURE/COOKIE_SAME_SITE.");
            return null;
        }

        return cookieCodec.decode(authorizationCookie.get().getValue(), OAuth2AuthorizationRequest.class)
                .orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            deleteAuthorizationRequestCookie(response);
            return;
        }

        ResponseCookie cookie = ResponseCookie
                .from(OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME, cookieCodec.encode(authorizationRequest))
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(Duration.ofSeconds(cookieExpireSeconds))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        log.debug("Saved OAuth authorization request cookie (secure={}, sameSite={}, maxAgeSeconds={})",
                secure, sameSite, cookieExpireSeconds);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        return this.loadAuthorizationRequest(request);
    }

    public void removeAuthorizationRequestCookies(HttpServletRequest request, HttpServletResponse response) {
        deleteAuthorizationRequestCookie(response);
    }

    private void deleteAuthorizationRequestCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie
                .from(OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
