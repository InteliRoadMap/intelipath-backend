package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.SetupStudentProfileRequest;
import com.inteliroadmap.backend.domain.dto.request.SetupUserProfileRequest;
import com.inteliroadmap.backend.domain.dto.response.UserResponse;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.repositories.StudentRepository;
import com.inteliroadmap.backend.domain.entity.Student;

import java.time.LocalDate;

import com.inteliroadmap.backend.security.JwtService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    /**
     * Set up or update a user's profile information.
     * Extracts the user's email from the provided JWT token and updates their details in the database.
     * Only fields that are not null in the request will be updated.
     *
     * @param request SetupUserProfileRequest containing the JWT token and profile data to update.
     * @return UserResponse containing the updated user's ID, full name, and role.
     * @throws ResourceNotFoundException if the token is invalid or the user does not exist.
     */
    @Transactional
    public UserResponse setupUserProfile(SetupUserProfileRequest request) {
        log.info("Profile Module: Setup User Profile Request received");

        String email = jwtService.extractEmail(request.getToken());
        if (email == null) {
            throw new ResourceNotFoundException("Invalid token");
        }

        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new ResourceNotFoundException("User not found");
        }

        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getYob() != null) user.setYob(LocalDate.parse(request.getYob()));
        if (request.getBio() != null) user.setBio(request.getBio());
        userRepository.save(user);

        return UserResponse.builder()
                .id(user.getUserId().toString())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .build();
    }

}
