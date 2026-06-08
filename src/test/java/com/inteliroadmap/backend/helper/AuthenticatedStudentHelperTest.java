package com.inteliroadmap.backend.helper;

import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.repositories.StudentRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticatedStudentHelperTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private StudentRepository studentRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getOrCreateStudentForUpdateCreatesMissingStudent() {
        String email = "student@example.com";
        User user = User.builder().userId(UUID.randomUUID()).email(email).build();
        Student savedStudent = Student.builder().studentId(UUID.randomUUID()).user(user).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null)
        );

        when(userRepository.findByEmailForUpdate(email)).thenReturn(Optional.of(user));
        when(studentRepository.findByUserForUpdate(user)).thenReturn(Optional.empty());
        when(studentRepository.save(any(Student.class))).thenReturn(savedStudent);

        Student result = new AuthenticatedStudentHelper(userRepository, studentRepository)
                .getOrCreateStudentForUpdate();

        assertEquals(savedStudent.getStudentId(), result.getStudentId());
    }
}
