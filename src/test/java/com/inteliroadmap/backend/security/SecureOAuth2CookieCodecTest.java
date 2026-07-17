package com.inteliroadmap.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecureOAuth2CookieCodecTest {

    private final SecureOAuth2CookieCodec codec = new SecureOAuth2CookieCodec("test-only-oauth-cookie-secret");

    @Test
    void roundTripsAuthenticatedPayload() {
        String encoded = codec.encode("oauth-state");

        assertEquals("oauth-state", codec.decode(encoded, String.class).orElseThrow());
    }

    @Test
    void roundTripsOAuthAuthorizationRequest() {
        OAuth2AuthorizationRequest request = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://accounts.example.test/authorize")
                .clientId("client-id")
                .redirectUri("https://api.example.test/login/oauth2/code/example")
                .state("state")
                .build();

        OAuth2AuthorizationRequest decoded = codec
                .decode(codec.encode(request), OAuth2AuthorizationRequest.class)
                .orElseThrow();

        assertEquals(request.getClientId(), decoded.getClientId());
        assertEquals(request.getState(), decoded.getState());
    }

    @Test
    void rejectsTamperedPayloadBeforeDeserialization() {
        String encoded = codec.encode("oauth-state");
        String tampered = (encoded.startsWith("A") ? "B" : "A")
                + encoded.substring(1);

        assertTrue(codec.decode(tampered, String.class).isEmpty());
    }
}
