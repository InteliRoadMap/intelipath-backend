package com.inteliroadmap.backend.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Reversible AES-256-GCM encryption for secrets we must read back later — namely
 * stored GitHub OAuth access tokens. Distinct from {@link TokenHashUtil}, which is a
 * one-way digest for refresh tokens we only ever compare, never recover.
 *
 * <p>The key is a Base64-encoded 256-bit value supplied via {@code github.token-enc-key}
 * (env {@code GITHUB_TOKEN_ENC_KEY}). When it is blank, {@link #isEnabled()} is false and
 * callers must skip token storage rather than persist plaintext.
 *
 * <p>Format of {@link #encrypt}'s output: Base64( 12-byte random IV || GCM ciphertext+tag ).
 * A fresh IV per call is mandatory for GCM — reusing an IV with the same key breaks its
 * confidentiality guarantees.
 */
@Component
@Slf4j
public class TokenCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int AES_256_KEY_BYTES = 32;

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenCipher(@Value("${github.token-enc-key:}") String base64Key) {
        SecretKeySpec resolved = null;
        if (base64Key != null && !base64Key.isBlank()) {
            try {
                byte[] keyBytes = Base64.getDecoder().decode(base64Key.trim());
                if (keyBytes.length != AES_256_KEY_BYTES) {
                    // Wrong-length key is a config error, not a runtime one: fail loud at
                    // startup-adjacent code paths rather than silently encrypt with a bad key.
                    throw new IllegalArgumentException(
                            "github.token-enc-key must decode to 32 bytes (256-bit), got " + keyBytes.length);
                }
                resolved = new SecretKeySpec(keyBytes, "AES");
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Invalid github.token-enc-key: " + e.getMessage(), e);
            }
        } else {
            log.warn("TokenCipher: no github.token-enc-key configured; GitHub token storage is disabled.");
        }
        this.keySpec = resolved;
    }

    /** True when a valid key is configured and token encryption/decryption can be used. */
    public boolean isEnabled() {
        return keySpec != null;
    }

    /**
     * Encrypts a plaintext token. Returns Base64(IV || ciphertext+tag).
     *
     * @throws IllegalStateException if encryption is disabled (no key configured)
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        requireEnabled();
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt token", e);
        }
    }

    /**
     * Decrypts a value produced by {@link #encrypt}. Returns {@code null} for {@code null}
     * input. Returns {@code null} (with a warning) if the stored value can't be decrypted —
     * e.g. the key was rotated — so a bad record degrades to "re-authenticate" instead of a 500.
     *
     * @throws IllegalStateException if encryption is disabled (no key configured)
     */
    public String decrypt(String encrypted) {
        if (encrypted == null) {
            return null;
        }
        requireEnabled();
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted);
            if (combined.length <= IV_LENGTH_BYTES) {
                log.warn("TokenCipher: stored token too short to contain IV + ciphertext; ignoring.");
                return null;
            }
            byte[] iv = new byte[IV_LENGTH_BYTES];
            byte[] ciphertext = new byte[combined.length - IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES);
            System.arraycopy(combined, IV_LENGTH_BYTES, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("TokenCipher: failed to decrypt stored token (key rotated or corrupt record): {}", e.getMessage());
            return null;
        }
    }

    private void requireEnabled() {
        if (keySpec == null) {
            throw new IllegalStateException(
                    "GitHub token encryption is disabled: set github.token-enc-key (GITHUB_TOKEN_ENC_KEY).");
        }
    }
}
