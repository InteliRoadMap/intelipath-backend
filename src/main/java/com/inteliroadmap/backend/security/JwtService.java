package com.inteliroadmap.backend.security;

import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
@Getter
@Slf4j
public class JwtService {

    @Value("${JWT_SECRET}")
    private String secretKey;

    @Value("${JWT_ACCESS_EXPIRATION}")
    private long accessExpiration;

    @Value("${JWT_REFRESH_EXPIRATION}")
    private long refreshExpiration;

    /**
     * Generate Access Token
     * @param email user email
     * @param role user role
     * @return JWT access token
     */
    public String generateAccessToken(String email, String role) {
        log.debug("Generating access token for: {}", email);

        try {
            return Jwts.builder()
                    .subject(email)
                    .claim("role", role)
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + accessExpiration))
                    .signWith(getSigningKey())
                    .compact();
        } catch (Exception e) {
            log.error("Error generating access token: {}", e.getMessage());
            throw new RuntimeException("Failed to generate access token", e);
        }
    }

    /**
     * Generate Refresh Token
     * @param email user email
     * @return JWT refresh token
     */
    public String generateRefreshToken(String email) {

        log.debug("Generating refresh token for: {}", email);
        try {
            return Jwts.builder()
                    .subject(email)
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
                    .signWith(getSigningKey())
                    .compact();
        } catch (Exception e) {
            log.error("Error generating refresh token: {}", e.getMessage());
            throw new RuntimeException("Failed to generate refresh token", e);
        }
    }

    /**
     * Validate JWT token
     *
     * @param token JWT token
     * @return true if token valid
     */
    public boolean isTokenValid(String token) {
        try {
            getClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("Token expired");
            return false;
        } catch (JwtException e) {
            log.warn("Token invalid");
            return false;
        }
    }

    /**
     * Extract email from token subject
     *
     * @param token JWT token
     * @return email
     */
    public String extractEmail(String token) {
        try {
            return getClaims(token).getSubject();
        } catch (Exception e) {
            log.warn("Cannot extract email");
            return null;
        }
    }

    /**
     * Extract role claim from token
     *
     * @param token JWT token
     * @return role
     */
    public String extractRole(String token) {
        try {
            return getClaims(token).get("role", String.class);
        } catch (Exception e) {
            log.warn("Cannot extract role");
            return null;
        }
    }

    /**
     * Parse JWT claims
     *
     * @param token JWT token
     * @return claims payload
     */
    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Generate signing key from secret
     *
     * @return SecretKey
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(
                secretKey.getBytes(StandardCharsets.UTF_8)
        );
    }


}
