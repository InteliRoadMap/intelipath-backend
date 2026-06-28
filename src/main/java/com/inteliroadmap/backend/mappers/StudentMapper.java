package com.inteliroadmap.backend.mappers;

import com.inteliroadmap.backend.domain.dto.response.StudentResponse;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.domain.entity.CareerRole;
import com.inteliroadmap.backend.repositories.CareerRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class StudentMapper {

    private final UserRepository userRepository;
    private final CareerRoleRepository careerRoleRepository;

    public StudentResponse toSetupProfileResponse(Student student) {
        User user = userRepository.findByUserId(student.getUserId());
        return StudentResponse.builder()
                .id(user.getUserId())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .careerId(student.getCareerId())
                .build();
    }

    public StudentResponse toProfileResponse(Student student) {
        User user = userRepository.findByUserId(student.getUserId());
        String careerName = null;
        if (student.getCareerId() != null) {
            Optional<CareerRole> optCareer = careerRoleRepository.findById(student.getCareerId());
            if (optCareer.isPresent()) {
                careerName = optCareer.get().getCareerName();
            }
        }

        return StudentResponse.builder()
                .id(student.getUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .yob(user.getYob())
                .bio(user.getBio())
                .university(student.getUniversity())
                .yearOfAdmission(student.getYearOfAdmission())
                .major(student.getMajor())
                .githubProfile(student.getGithubProfile())
                .careerId(student.getCareerId())
                .careerName(careerName)
                .build();
    }
}
