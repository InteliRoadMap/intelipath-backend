package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.*;
import com.inteliroadmap.backend.domain.dto.response.ForgotPasswordResponse;
import com.inteliroadmap.backend.domain.dto.response.RefreshResponse;
import com.inteliroadmap.backend.domain.dto.response.RegisterResponse;
import com.inteliroadmap.backend.domain.dto.response.UserResponse;
import com.inteliroadmap.backend.domain.entity.RefreshToken;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.domain.enums.UserRole;
import com.inteliroadmap.backend.domain.enums.UserStatus;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.repositories.RefreshTokenRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.security.JwtService;
import com.inteliroadmap.backend.utils.EmailUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;

    /**
     * Register new student account
     *
     * @param registerRequest RegisterRequest containing email, password, fullName
     * @return UserResponse containing JWT token and user info
     * @throws ResourceNotFoundException if email already exists
     */
    @Transactional
    public RegisterResponse registerAccount(RegisterRequest registerRequest) {
        log.info("Register Module: Register request received for email: {}", registerRequest.getEmail());

        //B1: Check duplicate email registration
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            log.warn("Register Module: Email already in use: {}", registerRequest.getEmail());
            throw new ResourceNotFoundException("Email already in use");
        }

        //B2: Build User entity from request
        User user = buildUser(registerRequest);
        userRepository.save(user);
        log.info("Register Module: User registered successfully: {}", registerRequest.getEmail());

        return RegisterResponse.builder()
                .message("Welcome to InteliPath," + user.getFullName())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .build();
    }

    /**
     * Authenticate user using email and password
     *
     * Validation:
     * 1. Verify email exists in database
     * 2. Verify password matches encoded password
     * 3. Verify account is not suspended
     *
     * @param loginRequest LoginRequest containing email and password
     * @return UserResponse containing JWT token and user info
     * @throws ResourceNotFoundException if email not found, wrong password, or account suspended
     */
    @Transactional
    public UserResponse loginAccount(LoginRequest loginRequest) {
        log.info("Login Module: Login request received for email: {}", loginRequest.getEmail());

        //B1: Find user bt email
        User user = userRepository.findByEmail(loginRequest.getEmail());
        if  (user == null) {
            log.warn("Login Module: User not found: {}", loginRequest.getEmail());
            throw new ResourceNotFoundException("User not found");
        }

        //B2: Verify password against BCrypt encoded
        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            log.warn("Login Module: Passwords don't match");
            throw new ResourceNotFoundException("Passwords don't match");
        }

        //B3: Prevent suspended account
        if (user.getUserStatus() == UserStatus.SUSPENDED) {
            log.warn("Login Module: User is Suspended");
            throw new ResourceNotFoundException("User is Suspended");
        }

        log.info("Login Module: User prepare to create Refresh token");
        LocalDateTime expiresIn = LocalDateTime.now().plus(Duration.ofMillis(jwtService.getRefreshExpiration()));
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());
        RefreshToken token = RefreshToken.builder()
                .token(refreshToken)
                .user(user)
                .expireAt(expiresIn)
                .build();
        refreshTokenRepository.save(token);
        log.info("Login Module: User logged in successfully: {}", loginRequest.getEmail());
        return buildAuthResponse(user, refreshToken, expiresIn);

    }

    @Transactional
    public RefreshResponse  refreshAccount(RefreshRequest refreshRequest) {
        log.info("Refresh access token");
        String refreshToken = refreshRequest.getRefreshToken();
        //B1: Check refresh token exists in DB
        RefreshToken storedToken = refreshTokenRepository.findByToken(refreshToken);
        if (storedToken == null) {
            log.warn("Refresh token not found");
            throw new ResourceNotFoundException("Refresh token not found");
        }

        //B2: Check expired in DB
        if (storedToken.getExpireAt().isBefore(LocalDateTime.now())){
            log.warn("Refresh token expired");
            if (refreshTokenRepository.deleteByToken(refreshToken)){
                log.warn("Refresh token deleted successfully");
            }
            throw new ResourceNotFoundException("Refresh token expired");
        }

        //B3: Check JWT token is valid
        if (!jwtService.isTokenValid(refreshToken)) {
            log.warn("Refresh token invalid");
            throw new ResourceNotFoundException("Refresh token invalid");
        }

        //B4: Check user from refresh token
        String email = jwtService.extractEmail(refreshToken);

        User user = userRepository.findByEmail(email);
        if (user == null) {
            log.warn("Refresh Module: User not found: {}", email);
            throw new ResourceNotFoundException("User not found");
        }

        //B5: Generate new access token
        String newAccessToken = jwtService.generateAccessToken(
                user.getEmail(),
                user.getRole().name()
        );
        log.info("New access token generated for : {}", user.getFullName());

        return refreshResponse(newAccessToken, jwtService.getAccessExpiration());

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
     * @return ForgotPasswordResponse with success message
     * @throws ResourceNotFoundException if email not found
     */
    @Transactional
    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request) {
        log.info("Forgot Password Module: Forgot password request received for email: {}", request.getEmail());

        // Find user by email
        User user = userRepository.findByEmail(request.getEmail());
        if (user == null) {
            log.warn("Forgot Password Module: User not found: {}", request.getEmail());
            throw new ResourceNotFoundException("User not found");
        }

        // Generate OTP
        String otp = EmailUtil.generateOtp();

        // Save OTP
        user.setOtp(otp);
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(2));
        userRepository.save(user);
        log.info("Forgot Password Module: OTP generated and saved for user: {}", request.getEmail());
        log.info("OTP: {}", otp);

        // Send email
        emailService.sendOtpEmail(user.getEmail(), otp);

        return ForgotPasswordResponse.builder()
                .message("OTP sent to email: ")
                .email(user.getEmail())
                .build();
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
     * @return UserResponse containing authenticated user info
     * @throws ResourceNotFoundException if email not found, OTP invalid or expired
     */
    @Transactional
    public UserResponse resetPassword(ResetPasswordRequest request) {
        log.info("Reset Password Module: Reset password request received for email: {}", request.getEmail());

        //1. Find user by email
        User user = userRepository.findByEmail(request.getEmail());
        if (user == null) {
            log.warn("Reset Password Module: User not found: {}", request.getEmail());
            throw new ResourceNotFoundException("User not found");
        }

        //2. Check OTP code
        if (user.getOtp() == null || !user.getOtp().equals(request.getOtp())) {
            log.warn("Reset Password Module: Invalid OTP code provided for user: {}", request.getEmail());
            throw new ResourceNotFoundException("Invalid OTP code");
        }

        log.info("OTP: {}", request.getOtp());

        //3. Check OTP expiry
        if (user.getOtpExpiry().isBefore(LocalDateTime.now())) {
            log.warn("Reset Password Module: OTP code expired for user: {}", request.getEmail());
            throw new ResourceNotFoundException("OTP expired");
        }

        //4. Update account with new password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));

        //5. Remove OTP
        user.setOtp(null);
        user.setOtpExpiry(null);
        userRepository.save(user);
        log.info("Reset Password Module: Password successfully reset for user: {}", request.getEmail());

        //6. Generate fresh tokens
        LocalDateTime expiresIn = LocalDateTime.now().plus(Duration.ofMillis(jwtService.getRefreshExpiration()));
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());
        RefreshToken token = RefreshToken.builder()
                .token(refreshToken)
                .user(user)
                .expireAt(LocalDateTime.now().plus(Duration.ofMillis(jwtService.getRefreshExpiration())))
                .build();
        refreshTokenRepository.save(token);
        return buildAuthResponse(user, refreshToken, expiresIn);

    }

    /**
     * Build UserResponse DTO from authenticated User entity
     * @param user Authenticated User entity
     * @return UserResponse containing JWT token and user info
     */
    public UserResponse buildAuthResponse(User user, String refreshToken, LocalDateTime expiresIn) {
        log.info("Build Auth Response for email: {}", user.getEmail());
        return UserResponse.builder()
                .accessToken(
                        jwtService.generateAccessToken(
                                user.getEmail(),
                                user.getRole().name()
                        )
                )
                .refreshToken(
                        jwtService.generateRefreshToken(
                                user.getEmail()
                        )
                )
                .expiresIn(String.valueOf(expiresIn))
                .id(user.getUserId().toString())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .build();
    }

    /**
     * Build new User entity from RegisterRequest
     * @param registerRequest RegisterRequest payload
     * @return User entity ready to be persisted
     */
   private User buildUser(RegisterRequest registerRequest) {
        log.debug("Build User with email: {}", registerRequest.getEmail());
        return User.builder()
                .email(registerRequest.getEmail())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .fullName(registerRequest.getFullName())
                .role(UserRole.STUDENT)
                .build();
   }

   private RefreshResponse refreshResponse(String accessToken, long expiresIn) {
        log.info("Refresh access token");
        return RefreshResponse.builder()
                .accessToken(accessToken)
                .expiresIn(String.valueOf(expiresIn))
                .build();

   }
}