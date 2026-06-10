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
import org.springframework.stereotype.Service;

import java.time.LocalDate;

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

        return studentMapper.toSetupProfileResponse(student);
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

        Student student = authenticatedStudentHelper.getOrCreateStudent();

        return studentMapper.toProfileResponse(student);
    }
}
