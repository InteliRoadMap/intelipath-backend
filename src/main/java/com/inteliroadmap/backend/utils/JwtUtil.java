package com.inteliroadmap.backend.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;
    @Value("${jwt.expiration}")
    private Long expiration;

    /**
     * Generate JWT token using user email.
     *
     * JWT contains:
     * - subject (email)
     * - issued time
     * - expiration time
     * - signature
     *
     * @param email authenticated user email
     * @return generated JWT token
     */
    public String generateToken(String email) {

        return Jwts.builder()

                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))

                .signWith(getSignKey())
                .compact(); //Built JWT String
    }

    /**
     * Extract email (subject) from JWT token.
     *
     * @param token JWT token
     * @return user email stored inside token
     */
    public String extractEmail(String token) {
        return getClaims(token).getSubject();
    }

    /**
     * Check whether JWT token is still valid.
     *
     * Validation includes: token signature, token expiration
     * @param token JWT token
     * @return true if token is not expired
     */
    public boolean isTokenValid(String token) {

        return !getClaims(token)
                .getExpiration()
                .before(new Date());
    }

    /**
     * Parse JWT token and extract claims payload.
     *
     * Claims usually contain: subject, expiration, issued time
     * @param token JWT token
     * @return JWT claims payload
     */
    private Claims getClaims(String token) {

        return Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Convert Base64 secret string into SecretKey object.
     *
     * SecretKey is used to:
     * - sign JWT token
     * - verify JWT token signature
     *
     * @return HMAC SHA secret key
     */
    private SecretKey getSignKey() {

        return Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(secret)
        );
    }
}