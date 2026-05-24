package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {

    // The user's email. Must not be blank and must follow standard email formatting.
    @NotBlank(message = "Email is required")
    @Email(message = "Email is invalid")
    private String email;

    // The user's plain-text password (which will be hashed later in AuthService).
    @NotBlank(message = "Password is required")
    @Size(min = 4, max = 20, message = "Password must be 4-20 characters")
    private String password;

    @NotBlank(message = "Full name is required")
    private String fullName;

}