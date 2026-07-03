package com.inteliroadmap.backend.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * Encrypts and authenticates short-lived OAuth2 state before it is stored in a client cookie.
 *
 * <p>Java deserialization is performed only after AES-GCM authentication succeeds, so an
 * attacker cannot supply an arbitrary serialized payload through the cookie.</p>
 */
@Component
@Slf4j
public class SecureOAuth2CookieCodec {

    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int NONCE_LENGTH_BYTES = 12;

    private final SecretKey encryptionKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecureOAuth2CookieCodec(
            @Value("${app.security.cookie.oauth-secret:${JWT_SECRET}}") String cookieSecret
    ) {
        if (cookieSecret == null || cookieSecret.isBlank()) {
            throw new IllegalStateException("OAuth cookie secret must be configured");
        }

        this.encryptionKey = new SecretKeySpec(sha256(cookieSecret), "AES");
    }

    public String encode(Object value) {
        byte[] serialized = SerializationUtils.serialize(value);
        if (serialized == null) {
            throw new IllegalArgumentException("OAuth authorization request cannot be serialized");
        }

        try {
            byte[] nonce = new byte[NONCE_LENGTH_BYTES];
            secureRandom.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            byte[] encrypted = cipher.doFinal(serialized);

            ByteBuffer payload = ByteBuffer.allocate(nonce.length + encrypted.length);
            payload.put(nonce);
            payload.put(encrypted);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.array());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to secure OAuth authorization request", exception);
        }
    }

    public <T> Optional<T> decode(String encodedValue, Class<T> expectedType) {
        try {
            byte[] payload = Base64.getUrlDecoder().decode(encodedValue);
            if (payload.length <= NONCE_LENGTH_BYTES) {
                return Optional.empty();
            }

            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] nonce = new byte[NONCE_LENGTH_BYTES];
            buffer.get(nonce);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            byte[] serialized = cipher.doFinal(encrypted);

            Object decoded = SerializationUtils.deserialize(serialized);
            return expectedType.isInstance(decoded)
                    ? Optional.of(expectedType.cast(decoded))
                    : Optional.empty();
        } catch (Exception exception) {
            log.warn("SecureOAuth2CookieCodec: Ignoring invalid OAuth authorization cookie: {}", exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to derive OAuth cookie encryption key", exception);
        }
    }
}
