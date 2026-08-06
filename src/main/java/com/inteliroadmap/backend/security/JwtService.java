package com.inteliroadmap.backend.security;

import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
@Getter
@Slf4j
public class JwtService {

    @Value("${JWT_SECRET}")
    private String secretKey;

    // Short-lived access token (default 15 minutes) keeps the blast radius of a
    // leaked/necessarily-revoked token small, since access tokens are stateless.
    @Value("${JWT_ACCESS_EXPIRATION:900000}")
    private long accessExpiration;

    // Refresh token default 7 days; it is DB-backed and revocable.
    @Value("${JWT_REFRESH_EXPIRATION:604800000}")
    private long refreshExpiration;

    /**
     * Generate Access Token
     * @param email user email
     * @param role user role
     * @return JWT access token
     */
    public String generateAccessToken(String email, String role) {
        log.debug("JwtService: Generating access token for: {}", email);

        try {
            return Jwts.builder()
                    .subject(email)
                    .claim("role", role)
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + accessExpiration))
                    .signWith(getSigningKey(), Jwts.SIG.HS256)
                    .compact();
        } catch (Exception e) {
            log.error("JwtService: Error generating access token: {}", e.getMessage());
            throw new RuntimeException("Failed to generate access token", e);
        }
    }

    /**
     * Generate Refresh Token
     * @param email user email
     * @return JWT refresh token
     */
    public String generateRefreshToken(String email) {

        log.debug("JwtService: Generating refresh token for: {}", email);
        try {
            return Jwts.builder()
                    .subject(email)
                    // A JWT's iat/exp are epoch SECONDS, so without this two logins
                    // inside the same second produced a byte-identical token, hence
                    // an identical SHA-256, hence a unique-constraint violation on
                    // refresh_tokens.token and a 500 on a perfectly valid login.
                    .id(UUID.randomUUID().toString())
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
                    .signWith(getSigningKey(), Jwts.SIG.HS256)
                    .compact();
        } catch (Exception e) {
            log.error("JwtService: Error generating refresh token: {}", e.getMessage());
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
            log.warn("JwtService: Token expired");
            return false;
        } catch (JwtException e) {
            log.warn("JwtService: Token invalid");
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
            log.warn("JwtService: Cannot extract email");
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
            log.warn("JwtService: Cannot extract role");
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
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token);
        // Defence-in-depth: never trust the client-supplied header algorithm.
        // Only HS256-signed tokens are accepted, so a downgrade or algorithm-swap
        // header can never route verification down an unexpected path.
        String alg = jws.getHeader().getAlgorithm();
        if (!Jwts.SIG.HS256.getId().equals(alg)) {
            throw new JwtException("Unexpected JWT algorithm: " + alg);
        }
        return jws.getPayload();
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
