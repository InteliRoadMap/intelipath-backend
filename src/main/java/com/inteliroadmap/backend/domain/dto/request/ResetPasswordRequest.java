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
public class ResetPasswordRequest {

    //User's email for targeting the right account
    @NotBlank(message = "Email is required")
    @Email(message = "Email is invalid")
    private String email;

    //OPT code to confirm the right user
    @NotBlank(message = "OTP is required")
    private String otp;

    //User's new password
    @NotBlank(message = "Password is required")
    @Size(min = 4, max = 20, message = "Password must be 4-20 characters")
    private String newPassword;
}
