package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.request.ChangePasswordRequest;
import com.inteliroadmap.backend.domain.dto.request.SetupUserProfileRequest;
import com.inteliroadmap.backend.domain.dto.request.UserRequest;
import com.inteliroadmap.backend.domain.dto.response.auth.UserResponse;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.exceptions.BadRequestException;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.exceptions.UnauthorizedException;
import com.inteliroadmap.backend.mappers.UserMapper;
import com.inteliroadmap.backend.repositories.RefreshTokenRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.services.SupabaseStorageService;
import com.inteliroadmap.backend.services.UserService;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/**
 * Implementation of the {@link UserService}.
 * Provides services related to user management including retrieving, setting up user profiles and updating avatars.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final SupabaseStorageService supabaseStorageService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Retrieves the information of the currently authenticated user.
     *
     * @return UserResponse containing the current user's details
     * @throws ResourceNotFoundException if the user cannot be extracted or found
     */
    @Transactional
    @Override
    public UserResponse getCurrentUser() {
        log.info("UserServiceImpl: Current user info request received");

        // Extract the authenticated user's email from the security context
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        if (email == null || email.isBlank()) {
            log.warn("UserServiceImpl: Cannot extract email from security context");
            throw new UnauthorizedException("Cannot extract email from security context");
        }

        // Retrieve the user entity from the database using the email
        User user = userRepository.findByEmail(email);
        if (user == null) {
            log.warn("UserServiceImpl: User not found: {}", email);
            throw new ResourceNotFoundException("User not found");
        }

        return userMapper.toUserResponse(user);
    }

    /**
     * Retrieves the information of a user based on the provided email in the request.
     *
     * @param userRequest the request containing the email of the user to fetch
     * @return UserResponse containing the user's details
     * @throws ResourceNotFoundException if the user is not found
     */
    @Transactional
    @Override
    public UserResponse getUserByEmail(UserRequest userRequest) {
        log.info("UserServiceImpl: User info request received for email: {}", userRequest.getEmail());

        User user = userRepository.findByEmail(userRequest.getEmail());
        if (user == null) {
            log.warn("UserServiceImpl: User not found: {}", userRequest.getEmail());
            throw new ResourceNotFoundException("User not found");
        }

        return userMapper.toUserResponse(user);
    }

    /**
     * Sets up or updates the profile of the currently authenticated user.
     *
     * @param request the request containing profile details such as full name, year of birth, and bio
     * @return UserResponse containing the updated user's details
     * @throws ResourceNotFoundException if the user is not found
     */
    @Transactional
    @Override
    public UserResponse setupUserProfile(SetupUserProfileRequest request) {
        log.info("UserServiceImpl: Setup user profile request received");

        // Identify and fetch the current user based on the security context
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new ResourceNotFoundException("User not found");
        }

        // Apply non-null fields from the setup request to the user entity
        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getYob() != null && !request.getYob().trim().isEmpty()) {
            user.setYob(LocalDate.parse(request.getYob()));
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }

        // Persist the updated user profile
        userRepository.save(user);

        return userMapper.toUserResponse(user);
    }

    /**
     * Updates the avatar of the currently authenticated user.
     *
     * @param file the multipart file representing the new avatar image
     * @return UserResponse containing the updated user's details including the new avatar URL
     * @throws ResourceNotFoundException if the user is not found
     */
    @Transactional
    @Override
    public UserResponse updateAvatar(MultipartFile file) {
        log.info("UserServiceImpl: Update avatar request received");

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new ResourceNotFoundException("User not found");
        }

        String publicUrl = supabaseStorageService.uploadAvatar(file, user.getUserId().toString());
        user.setAvatarUrl(publicUrl);
        userRepository.save(user);

        return userMapper.toUserResponse(user);
    }

    /**
     * Changes the password of the currently authenticated account. Works the same for every
     * role (student, mentor, counselor, admin) since they all share the {@code users} table
     * and this reads the account from the security context rather than a role-specific path.
     *
     * @throws BadRequestException if the account has no local password (OAuth-only) or the
     *                              submitted current password does not match
     */
    @Transactional
    @Override
    public void changePassword(ChangePasswordRequest request) {
        log.info("UserServiceImpl: Change password request received");

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new ResourceNotFoundException("User not found");
        }

        if (user.getPasswordHash() == null) {
            throw new BadRequestException("This account signs in via OAuth and has no password to change");
        }

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // A password change should end existing sessions on other devices, same as a
        // magic-link reset.
        refreshTokenRepository.deleteByUser_UserId(user.getUserId());
        log.info("UserServiceImpl: Password changed successfully for user: {}", user.getEmail());
    }
}
