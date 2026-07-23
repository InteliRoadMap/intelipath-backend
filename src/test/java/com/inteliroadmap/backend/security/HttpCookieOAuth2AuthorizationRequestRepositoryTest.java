package com.inteliroadmap.backend.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpCookieOAuth2AuthorizationRequestRepositoryTest {

    @Test
    void storesAndLoadsAuthenticatedOAuthRequestFromCookie() {
        HttpCookieOAuth2AuthorizationRequestRepository repository =
                new HttpCookieOAuth2AuthorizationRequestRepository(new SecureOAuth2CookieCodec("test-only-oauth-cookie-secret"));
        OAuth2AuthorizationRequest authorizationRequest = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://accounts.example.test/authorize")
                .clientId("client-id")
                .redirectUri("http://localhost:8080/login/oauth2/code/google")
                .state("csrf-state")
                .build();
        MockHttpServletResponse startResponse = new MockHttpServletResponse();

        repository.saveAuthorizationRequest(authorizationRequest, new MockHttpServletRequest(), startResponse);

        Cookie storedCookie = startResponse.getCookie(
                HttpCookieOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME
        );
        assertNotNull(storedCookie);
        assertTrue(storedCookie.isHttpOnly());

        MockHttpServletRequest callbackRequest = new MockHttpServletRequest();
        callbackRequest.setCookies(new Cookie(
                HttpCookieOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME,
                storedCookie.getValue()
        ));

        OAuth2AuthorizationRequest restored = repository.loadAuthorizationRequest(callbackRequest);

        assertNotNull(restored);
        assertEquals(authorizationRequest.getState(), restored.getState());
        assertEquals(authorizationRequest.getRedirectUri(), restored.getRedirectUri());
    }
}
