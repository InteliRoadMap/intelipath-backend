package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Credentials for an account that was provisioned rather than self-registered:
 * staff (counselor, mentor, admin) and FPT students. Accounts created through
 * OAuth have no username or password and cannot sign in this way.
 */
@Data
public class LoginRequest {

    @NotBlank(message = "Username is required")
    @Size(max = 100, message = "Username must not exceed 100 characters")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(max = 200, message = "Password must not exceed 200 characters")
    private String password;
}
