package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.ForgotPasswordRequest;
import com.inteliroadmap.backend.domain.dto.request.LoginRequest;
import com.inteliroadmap.backend.domain.dto.request.RegisterRequest;
import com.inteliroadmap.backend.domain.dto.request.ResetPasswordRequest;
import com.inteliroadmap.backend.domain.dto.response.ApiResponse;
import com.inteliroadmap.backend.domain.dto.response.UserResponse;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.domain.enums.UserRole;
import com.inteliroadmap.backend.domain.enums.UserStatus;
import com.inteliroadmap.backend.exceptions.AppException;
import com.inteliroadmap.backend.exceptions.enums.ErrorCode;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.utils.EmailUtil;
import com.inteliroadmap.backend.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;

    /**
     * Register new user account.
     *
     * Flow:
     * - Check email already exists
     * - Encode password
     * - Save user into database
     * - Generate JWT token
     *
     * @param request register request payload
     * @return API response containing authenticated user info
     */
    public ApiResponse<UserResponse> registerAccount(RegisterRequest request) {

        // Prevent duplicate email registration
        if (userRepository.existsByEmail(request.getEmail())) {
            return ApiResponse.error(400, "Email already exists");
        }

        // Build new user entity
        User user = buildUser(request);

        // Save user into database
        userRepository.save(user);

        return ApiResponse.success(
                201, "Register successful", buildAuthResponse(user)
        );
    }

    /**
     * Authenticate user using email and password.
     *
     * Validation:
     * - Email exists
     * - Password matches
     * - Account is active
     *
     * @param request login request payload
     * @return API response containing JWT token and user info
     */
    public ApiResponse<UserResponse> loginAccount(LoginRequest request) {

        // Find user by email
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.EMAIL_NOT_FOUND));

        // Verify User Password
        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword()
        )) {throw new AppException(ErrorCode.WRONG_PASSWORD);}

        // Prevent suspended users from logging in
        if (user.getUserStatus() == UserStatus.SUSPENDED) {throw new AppException(ErrorCode.ACCOUNT_SUSPENDED);}

        return ApiResponse.success(
                200, "Login successful", buildAuthResponse(user)
        );
    }

    /**
     * Initiate the forgot password flow.
     *
     * Flow:
     * - Find user by email
     * - Generate a new OTP
     * - Save the OTP and set its expiration (5 minutes)
     * - Send the OTP to the user's email
     *
     * @param request forgot password request payload (contains email)
     * @return API response with success message
     */
    public ApiResponse<String> forgotPassword(ForgotPasswordRequest request) {

        // Find user by email
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.EMAIL_NOT_FOUND));

        //Generate OTP
        String otp = EmailUtil.generateOtp();

        //Save OTP
        user.setOtp(otp);
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(5));
        userRepository.save(user);

        //Send email
        emailService.sendOtpEmail(user.getEmail(), otp);

        return ApiResponse.success(
                200, "OTP sent to email: " + user.getEmail(), null
        );
    }

    /**
     * Complete the password reset process.
     *
     * Flow:
     * - Verify user exists
     * - Check if the provided OTP matches the one saved in the database
     * - Check if the OTP has expired
     * - Encode and save the new password
     * - Invalidate the OTP to prevent reuse
     *
     * @param request reset password request payload (contains email, OTP, and new password)
     * @return API response containing authenticated user info
     */
    public ApiResponse<UserResponse> resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.EMAIL_NOT_FOUND));

        //Check OTP code
        if(user.getOtp() == null || !user.getOtp().equals(request.getOtp())) {
            throw new AppException(ErrorCode.INVALID_OTP_CODE);
        }

        //Check OTP expiry
        if(user.getOtpExpiry().isBefore(LocalDateTime.now())) {
            return ApiResponse.error(400, "OTP expired");
        }

        //Update account with new password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));

        //Remove OTP
        user.setOtp(null);
        user.setOtpExpiry(null);
        userRepository.save(user);

        return ApiResponse.success(
                200, "Reset Password successful", buildAuthResponse(user)
        );
    }

    /**
     * Build new User entity from register request.
     * @param request register request payload
     * @return new User entity
     */
    private User buildUser(RegisterRequest request) {

        return User.builder()

                .email(request.getEmail())
                .password(
                        passwordEncoder.encode(request.getPassword())
                )
                .fullName(request.getFullName())
                .role(UserRole.STUDENT)

                .build();
    }

    /**
     * Build authenticated user response.
     * @param user authenticated user
     * @return user response DTO
     */
    private UserResponse buildAuthResponse(User user) {

        return UserResponse.builder()

                .accessToken(
                        jwtUtil.generateToken(user.getEmail())
                )

                .userId(user.getUserId().toString())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())

                .build();
    }
}