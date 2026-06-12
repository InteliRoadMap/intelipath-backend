package com.inteliroadmap.backend.helper;

import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.repositories.StudentRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthenticatedStudentHelper {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;

    /**
     * Get current authenticated student and create one if missing.
     *
     * @return current authenticated student
     */
    public Student getOrCreateStudent() {
        User user = getAuthenticatedUser();
        Student student = studentRepository.findById(user.getUserId()).orElse(null);
        if (student == null) {
            log.info("Student profile not found. Creating a new one for user: {}", user.getEmail());
            student = studentRepository.save(Student.builder().userId(user.getUserId()).build());
        }
        return student;
    }

    /**
     * Get the current authenticated student without creating missing profile data.
     *
     * @return current authenticated student
     */
    public Student getRequiredStudent() {
        User user = getAuthenticatedUser();
        Student student = studentRepository.findByUser(user);
        if (student == null) {
            throw new ResourceNotFoundException("Student profile not found");
        }
        return student;
    }

    /**
     * Get current authenticated student for update and create one if missing.
     *
     * @return locked current authenticated student
     */
    public Student getOrCreateStudentForUpdate() {
        String email = getAuthenticatedEmail();

        Optional<User> userOptional = userRepository.findByEmailForUpdate(email);
        if (userOptional.isEmpty()) {
            throw new ResourceNotFoundException("User not found from token");
        }

        User user = userOptional.get();
        Optional<Student> studentOptional = studentRepository.findByIdForUpdate(user.getUserId());
        if (studentOptional.isPresent()) {
            return studentOptional.get();
        }

        Student student = Student.builder()
                .userId(user.getUserId())
                .build();

        return studentRepository.save(student);
    }

    private User getAuthenticatedUser() {
        String email = getAuthenticatedEmail();
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new ResourceNotFoundException("User not found from token");
        }
        return user;
    }

    private String getAuthenticatedEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResourceNotFoundException("Cannot extract user from security context");
        }
        return authentication.getName();
    }
}
