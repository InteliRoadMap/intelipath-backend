package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.exceptions.UnauthorizedException;
import com.inteliroadmap.backend.repositories.StudentRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.services.AuthenticatedStudentService;
import com.inteliroadmap.backend.services.PortfolioSlugService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service implementation for managing and retrieving authenticated student profiles.
 * Automatically handles the creation of student profiles for authenticated users if they do not exist.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticatedStudentServiceImpl implements AuthenticatedStudentService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final PortfolioSlugService portfolioSlugService;

    /**
     * Get current authenticated student and create one if missing.
     *
     * @return current authenticated student
     */
    @Override
    public Student getOrCreateStudent() {
        // Fetch current authenticated user and find their associated student profile
        User user = getAuthenticatedUser();
        Student student = studentRepository.findById(user.getUserId()).orElse(null);
        
        // If no student profile exists, create a new default one with a generated portfolio slug
        if (student == null) {
            log.info("AuthenticatedStudentServiceImpl: Student profile not found. Creating a new one for user: {}", user.getEmail());
            student = studentRepository.save(Student.builder()
                    .userId(user.getUserId())
                    .portfolioSlug(portfolioSlugService.allocateFor(user))
                    .build());
        }
        return student;
    }

    /**
     * Get the current authenticated student without creating missing profile data.
     *
     * @return current authenticated student
     */
    @Override
    public Student getRequiredStudent() {
        // Retrieve the user and their required student profile
        User user = getAuthenticatedUser();
        Student student = studentRepository.findById(user.getUserId()).orElse(null);
        
        // Throw an exception if the student profile is mandatory but missing
        if (student == null) {
            log.error("AuthenticatedStudentServiceImpl: Student profile not found for user ID: {}", user.getUserId());
            throw new ResourceNotFoundException("Student profile not found");
        }
        
        // Initialize the portfolio slug if it was previously empty
        if (student.getPortfolioSlug() == null || student.getPortfolioSlug().isBlank()) {
            student.setPortfolioSlug(portfolioSlugService.allocateFor(user));
            student = studentRepository.save(student);
        }
        return student;
    }

    /**
     * Get current authenticated student for update and create one if missing.
     *
     * @return locked current authenticated student
     */
    @Override
    public Student getOrCreateStudentForUpdate() {
        // Identify the current user via email
        String email = getAuthenticatedEmail();

        // Attempt to fetch the user with a database lock to prevent concurrent modifications
        Optional<User> userOptional = userRepository.findByEmailForUpdate(email);
        if (userOptional.isEmpty()) {
            log.error("AuthenticatedStudentServiceImpl: User not found from token: {}", email);
            throw new UnauthorizedException("User not found from token");
        }

        // Retrieve or lock the associated student profile
        User user = userOptional.get();
        Optional<Student> studentOptional = studentRepository.findByIdForUpdate(user.getUserId());
        if (studentOptional.isPresent()) {
            return studentOptional.get();
        }

        // If no student profile exists under lock, create and persist a new one
        Student student = Student.builder()
                .userId(user.getUserId())
                .portfolioSlug(portfolioSlugService.allocateFor(user))
                .build();

        return studentRepository.save(student);
    }

    /**
     * Retrieves the current authenticated user from the database.
     *
     * @return the authenticated User entity
     * @throws ResourceNotFoundException if the user cannot be found using the security context email
     */
    @Override
    public User getAuthenticatedUser() {
        // Use the authenticated email to find the user in the database
        String email = getAuthenticatedEmail();
        User user = userRepository.findByEmail(email);
        
        // Throw an exception if the user record doesn't exist
        if (user == null) {
            log.error("AuthenticatedStudentServiceImpl: User not found from token: {}", email);
            throw new UnauthorizedException("User not found from token");
        }
        return user;
    }

    /**
     * Extracts the email address of the currently authenticated user from the security context.
     *
     * @return the email address of the authenticated user
     * @throws ResourceNotFoundException if the authentication context is missing or empty
     */
    @Override
    public String getAuthenticatedEmail() {
        // Extract the authentication object from the current security context
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        // Validate that the authentication information is present and contains the email/username
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            log.error("AuthenticatedStudentServiceImpl: Cannot extract user from security context");
            throw new UnauthorizedException("Cannot extract user from security context");
        }
        
        // Return the authenticated username (typically the email)
        return authentication.getName();
    }
}
