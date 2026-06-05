package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.SetupStudentProfileRequest;
import com.inteliroadmap.backend.domain.dto.response.SkillResponse;
import com.inteliroadmap.backend.domain.dto.response.StudentResponse;
import com.inteliroadmap.backend.domain.dto.response.UserResponse;
import com.inteliroadmap.backend.domain.entity.CareerRole;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.repositories.CareerRoleRepository;
import com.inteliroadmap.backend.repositories.StudentRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.repositories.StudentSkillRepository;
import com.inteliroadmap.backend.security.JwtService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final CareerRoleRepository careerRoleRepository;
    private final JwtService jwtService;

    /**
     * securely extracts the authenticated user's email from the SecurityContextHolder 
     * (populated by JwtAuthenticationFilter) and retrieves their associated Student profile.
     *
     * @return The authenticated Student entity
     * @throws ResourceNotFoundException if the token is invalid, user is missing, or student profile is missing
     */
    private Student getAuthenticatedStudent() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new ResourceNotFoundException("User not found from token");
        }
        Student student = studentRepository.findByUser(user);
        if (student == null) {
            log.info("Student profile not found. Creating a new one for user: {}", email);
            student = Student.builder().user(user).build();
            student = studentRepository.save(student);
        }
        return student;
    }

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
    public StudentResponse setupStudentProfile(SetupStudentProfileRequest request) {
        log.info("Student Module: Setup Student Profile Request received");

        Student student = getAuthenticatedStudent();
        User user = student.getUser();
        if (user == null) {
            throw new ResourceNotFoundException("User not found");
        }

        if (request.getUniversity() != null) student.setUniversity(request.getUniversity());
        if (request.getYearOfAdmission() != null) {
            if (request.getYearOfAdmission().trim().isEmpty()) {
                student.setYearOfAdmission(null);
            } else {
                student.setYearOfAdmission(LocalDate.parse(request.getYearOfAdmission()));
            }
        }
        if (request.getMajor() != null) student.setMajor(request.getMajor());

        if (request.getCareerId() != null) {
            CareerRole career = careerRoleRepository.findByCareerId(request.getCareerId());
            if(career != null) {
                student.setCareerRole(career);
            }
        }
        
        boolean userChanged = false;
        if (request.getBio() != null) {
            user.setBio(request.getBio());
            userChanged = true;
        }
        if (request.getYob() != null) {
            if (request.getYob().trim().isEmpty()) {
                user.setYob(null);
            } else {
                user.setYob(LocalDate.parse(request.getYob()));
            }
            userChanged = true;
        }

        if (userChanged) {
            userRepository.save(user);
        }

        studentRepository.save(student);

        return StudentResponse.builder()
                .id(student.getStudentId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .yob(user.getYob())
                .bio(user.getBio())
                .university(student.getUniversity())
                .yearOfAdmission(student.getYearOfAdmission())
                .major(student.getMajor())
                .githubProfile(student.getGithubProfile())
                .role(user.getRole().name())
                .careerId(student.getCareerRole() != null ? student.getCareerRole().getCareerId() : null)
                .build();
    }

    /**
     * Retrieves the profile information for the currently authenticated student.
     * Uses the JWT token to identify the student rather than trusting client-provided IDs.
     *
     * @return StudentResponse containing the student's personal and academic details
     */
    @Transactional
    public StudentResponse getStudentProfile() {
        log.info("Student Module: Get Student Profile Request received");

        Student student = getAuthenticatedStudent();

        User user = student.getUser();

        return StudentResponse.builder()
                .id(student.getStudentId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .yob(user.getYob())
                .bio(user.getBio())
                .university(student.getUniversity())
                .yearOfAdmission(student.getYearOfAdmission())
                .major(student.getMajor())
                .githubProfile(student.getGithubProfile())
                .careerId(student.getCareerRole() != null ? student.getCareerRole().getCareerId() : null)
                .build();
    }
}
