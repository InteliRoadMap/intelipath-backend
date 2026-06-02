package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.SetupStudentProfileRequest;
import com.inteliroadmap.backend.domain.dto.response.UserResponse;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.repositories.StudentRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.security.JwtService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final JwtService jwtService;

    /**
     * Set up or update a student's profile information.
     * Extracts the user's email from the provided JWT token and updates their details in the database.
     * Only fields that are not null in the request will be updated.
     *
     * @param request SetupStudentProfileRequest containing the JWT token and profile data to update.
     * @return UserResponse containing the updated user's ID, full name, and role.
     * @throws ResourceNotFoundException if the token is invalid or the user does not exist.
     */
    @Transactional
    public UserResponse setupStudentProfile(SetupStudentProfileRequest request) {
        log.info("Profile Module: Setup Student Profile Request received");

        String email = jwtService.extractEmail(request.getToken());
        if (email == null) {
            throw new ResourceNotFoundException("Invalid token");
        }

        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new ResourceNotFoundException("User not found");
        }

        Student student = studentRepository.findByUser_UserId(user.getUserId());
        if (student == null) {
            student = Student.builder().user(user).build();
        }
        if (request.getUniversity() != null) student.setUniversity(request.getUniversity());
        if (request.getYear_of_admission() != null) student.setYearOfAdmission(LocalDate.parse(request.getYear_of_admission()));
        if (request.getMajor() != null) student.setMajor(request.getMajor());
        studentRepository.save(student);

        return UserResponse.builder()
                .id(user.getUserId().toString())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .build();
    }


}
