package com.inteliroadmap.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Credentials for the seeded dev accounts, supplied per environment via .env.
 *
 * They are deliberately not hard-coded: a password in the source is a password
 * everyone with repo access knows. Leave a pair unset and that account is seeded
 * without a local credential, so it can only sign in through OAuth.
 */
@Component
@ConfigurationProperties(prefix = "seed")
@Data
public class SeedAccountsProperties {

    private Account admin = new Account();
    private Account counselor = new Account();
    private Account student = new Account();
    private Account demoStudent = new Account();
    private Account mentor = new Account();
    @Data
    public static class Account {
        private String username;
        private String password;

        /** True only when both halves are present; a half-configured pair is unusable. */
        public boolean isUsable() {
            return username != null && !username.isBlank()
                    && password != null && !password.isBlank();
        }
    }
}
