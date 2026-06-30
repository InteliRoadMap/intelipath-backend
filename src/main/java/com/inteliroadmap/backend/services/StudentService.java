package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.response.SkillResponse;
import com.inteliroadmap.backend.domain.dto.response.StudentResponse;
import com.inteliroadmap.backend.domain.entity.*;
import com.inteliroadmap.backend.domain.enums.RoadmapStepStatus;
import com.inteliroadmap.backend.services.AuthenticatedStudentService;
import com.inteliroadmap.backend.mappers.SkillMapper;
import com.inteliroadmap.backend.mappers.StudentDashboardMapper;
import com.inteliroadmap.backend.mappers.StudentMapper;
import com.inteliroadmap.backend.repositories.*;
import com.inteliroadmap.backend.services.StudentService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.*;

public interface StudentService {

    public StudentResponse setupStudentProfile(com.inteliroadmap.backend.domain.dto.request.SetupStudentProfileRequest request) ;

    public StudentResponse getStudentProfile() ;

    public StudentResponse updateTargetCareer(UUID careerId) ;

    public SkillResponse compareCurrentStudentSkills() ;

    public List<CareerRequiredSkill> findMissingRequiredSkills(Student student) ;

    public Integer calculateSkillProgress(Student student, UUID skillId) ;
}
