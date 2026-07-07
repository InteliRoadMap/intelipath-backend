package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.response.roadmap.SkillResponse;
import com.inteliroadmap.backend.domain.dto.response.student.StudentResponse;
import com.inteliroadmap.backend.domain.entity.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

public interface StudentService {

    StudentResponse setupStudentProfile(com.inteliroadmap.backend.domain.dto.request.SetupStudentProfileRequest request);

    StudentResponse getStudentProfile();

    StudentResponse updateTargetCareer(UUID careerId);

    SkillResponse compareCurrentStudentSkills();

    List<CareerRequiredSkill> findMissingRequiredSkills(Student student);

    Integer calculateSkillProgress(Student student, UUID skillId);

    StudentResponse uploadTranscript(MultipartFile file);
}
