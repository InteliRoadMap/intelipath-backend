package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.SetupStudentProfileRequest;
import com.inteliroadmap.backend.domain.dto.response.StudentResponse;
import com.inteliroadmap.backend.domain.entity.CareerRole;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.helper.AuthenticatedStudentHelper;
import com.inteliroadmap.backend.mappers.StudentMapper;
import com.inteliroadmap.backend.repositories.CareerRoleRepository;
import com.inteliroadmap.backend.repositories.StudentRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final CareerRoleRepository careerRoleRepository;
    private final AuthenticatedStudentHelper authenticatedStudentHelper;
    private final StudentMapper studentMapper;

    /**
     * Set up or update a student's profile information.
     * The student is resolved from the authenticated JWT security context.
     *
     * @param request request body containing student profile fields
     * @return StudentResponse containing the updated student profile
     */
    @Transactional
    public StudentResponse setupStudentProfile(SetupStudentProfileRequest request) {
        log.info("Student Module: Setup Student Profile Request received");

        Student student = authenticatedStudentHelper.getOrCreateStudentForUpdate();
        User user = userRepository.findByUserId(student.getUserId());
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

        if (request.getMajor() != null) {
            student.setMajor(request.getMajor());
        }

        // Step 4: Validate and update the career when careerId is supplied
        if (request.getCareerId() != null) {
            CareerRole career = careerRoleRepository.findByCareerId(request.getCareerId());
            if (career == null) {
                log.warn("Career role was not found: {}", request.getCareerId());
                throw new ResourceNotFoundException("Career role not found");
            }
            student.setCareerRole(career);
        }

        // Step 5: Save the updated student profile

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

        // Step 6: Map the saved student entity to the API response
        log.info("Student profile updated successfully for user: {}", user.getEmail());
        return studentMapper.toProfileResponse(student);
    }

    /**
     * Retrieves the profile information for the currently authenticated student.
     * Uses the JWT token to identify the student rather than trusting client-provided IDs.
     *
     * @return StudentResponse containing the student's personal and academic details
     */
    @Transactional
    public StudentResponse getStudentProfile() {
        log.info("Student profile retrieval request received");

        // Step 1: Get the authenticated student without creating profile data
        Student student = authenticatedStudentHelper.getRequiredStudent();

        // Step 2: Map the student entity to the API response
        return studentMapper.toProfileResponse(student);
    }

    /**
     * Updates the authenticated student's target career.
     * Business rule: careerId must be a real UUID that exists in career_roles.
     *
     * @param careerId target career UUID from request body
     * @return updated student profile response
     */
    @Transactional
    public StudentResponse updateTargetCareer(UUID careerId) {
        log.info("Student target career update request received. careerId: {}", careerId);

        // Step 1: Get or create the authenticated student for update
        Student student = authenticatedStudentHelper.getOrCreateStudentForUpdate();

        // Step 2: Verify that the requested career exists in the database
        CareerRole career = careerRoleRepository.findByCareerId(careerId);
        if (career == null) {
            log.warn("Career role was not found: {}", careerId);
            throw new ResourceNotFoundException("Career role not found");
        }

        // Step 3: Assign the validated career to the student
        student.setCareerRole(career);

        // Step 4: Save the updated student
        studentRepository.save(student);

        // Step 5: Return the updated student profile
        log.info("Student target career updated successfully. careerId: {}", careerId);
        return studentMapper.toProfileResponse(student);
    }

    /**
     * Parse an ISO date without changing or guessing the client value.
     *
     * @param dateValue date value in yyyy-MM-dd format
     * @return parsed LocalDate value
     */
    private LocalDate parseIsoDate(String dateValue) {
        try {
            return LocalDate.parse(dateValue);
        } catch (DateTimeParseException exception) {
            log.warn("Invalid yearOfAdmission format: {}", dateValue);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "yearOfAdmission must use yyyy-MM-dd format"
            );
        }
    }
}
