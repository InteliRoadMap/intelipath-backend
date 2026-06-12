package com.inteliroadmap.backend.mappers;

import com.inteliroadmap.backend.domain.dto.response.StudentResponse;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.User;
import org.springframework.stereotype.Component;

@Component
public class StudentMapper {

    public StudentResponse toSetupProfileResponse(Student student) {
        User user = student.getUser();
        return StudentResponse.builder()
                .id(user.getUserId())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .careerId(student.getCareerRole() != null ? student.getCareerRole().getCareerId() : null)
                .build();
    }

    public StudentResponse toProfileResponse(Student student) {
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
                .careerName(student.getCareerRole() != null ? student.getCareerRole().getCareerName() : null)
                .build();
    }
}
