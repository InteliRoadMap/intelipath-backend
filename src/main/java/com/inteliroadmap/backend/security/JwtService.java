package com.inteliroadmap.backend.security;


import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
@Slf4j
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    public String generateToken(String email, String role) {

        log.debug("Generating token for: {}", email);

        try {

            return Jwts.builder()
                    .subject(email)
                    .claim("role", role)
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + expiration))
                    .signWith(getSigningKey())
                    .compact();

        } catch (Exception e) {

            log.error("Error generating token: {}", e.getMessage());

            throw new RuntimeException("Failed to generate token", e);
        }
    }

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

    public String extractEmail(String token) {
        try {
            return getClaims(token).getSubject();
        } catch (Exception e) {
            log.warn("Cannot extract email");
            return null;
        }
    }

    public String extractRole(String token) {
        try {
            return getClaims(token).get("role", String.class);
        } catch (Exception e) {
            log.warn("Cannot extract role");
            return null;
        }

    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(secret)
        );
    }
}
