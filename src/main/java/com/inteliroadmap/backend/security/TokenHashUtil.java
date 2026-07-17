package com.inteliroadmap.backend.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Hashes refresh tokens before they are persisted. Refresh tokens are already
 * high-entropy signed JWTs, so a fast unsalted SHA-256 is sufficient: a database
 * leak then exposes only irreversible digests, not usable tokens. The plaintext
 * token still travels to the client in the HttpOnly cookie; only its digest is stored.
 */
public final class TokenHashUtil {

    private TokenHashUtil() {
    }

    public static String sha256Hex(String token) {
        if (token == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandated JDK algorithm; this should never happen.
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
