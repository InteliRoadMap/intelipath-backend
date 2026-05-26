package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.request.ForgotPasswordRequest;
import com.inteliroadmap.backend.domain.dto.request.LoginRequest;
import com.inteliroadmap.backend.domain.dto.request.RegisterRequest;
import com.inteliroadmap.backend.domain.dto.request.ResetPasswordRequest;
import com.inteliroadmap.backend.domain.dto.response.ApiResponse;
import com.inteliroadmap.backend.domain.dto.response.UserResponse;
import com.inteliroadmap.backend.services.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login, Register, Reset Password")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register new account")
    public ResponseEntity<ApiResponse<UserResponse>> register(@RequestBody @Valid RegisterRequest request) {
        return ResponseEntity.ok(authService.registerAccount(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with Email/Password")
    public ResponseEntity<ApiResponse<UserResponse>> login(@RequestBody @Valid LoginRequest request) {
        return ResponseEntity.ok(authService.loginAccount(request));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Forgot password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(@RequestBody @Valid ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.forgotPassword(request));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password")
    public ResponseEntity<ApiResponse<UserResponse>> resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
        return ResponseEntity.ok(authService.resetPassword(request));
    }
}
