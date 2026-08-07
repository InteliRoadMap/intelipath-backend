package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.components.SeniorityCalculator;
import com.inteliroadmap.backend.domain.entity.CareerRole;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.StudentAssessment;
import com.inteliroadmap.backend.domain.enums.SeniorityLevel;
import com.inteliroadmap.backend.mappers.StudentAssessmentMapper;
import com.inteliroadmap.backend.repositories.StudentAssessmentRepository;
import com.inteliroadmap.backend.repositories.StudentRepository;
import com.inteliroadmap.backend.services.impl.StudentLevelServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentLevelServiceImplTest {

    @Mock AuthenticatedStudentService authenticatedStudentService;
    @Mock StudentRepository studentRepository;
    @Mock StudentAssessmentRepository assessmentRepository;
    @Mock SeniorityCalculator seniorityCalculator;

    @Test
    void retakeCannotEraseHigherCompletedAssessment() {
        UUID userId = UUID.randomUUID();
        UUID careerId = UUID.randomUUID();
        Student student = Student.builder()
                .userId(userId)
                .careerRole(CareerRole.builder().careerId(careerId).careerName("Backend").build())
                .build();
        StudentAssessment latestFresher = StudentAssessment.builder()
                .userId(userId).careerId(careerId).aiLevel(SeniorityLevel.FRESHER)
                .aiRawLevel(SeniorityLevel.FRESHER).status("COMPLETED").build();
        StudentAssessment earlierMid = StudentAssessment.builder()
                .userId(userId).careerId(careerId).aiLevel(SeniorityLevel.MID)
                .aiRawLevel(SeniorityLevel.JUNIOR).status("COMPLETED").build();

        when(authenticatedStudentService.getRequiredStudent()).thenReturn(student);
        when(assessmentRepository.findByUserIdAndCareerIdAndStatusOrderByCreatedAtDesc(
                userId, careerId, "COMPLETED"))
                .thenReturn(List.of(latestFresher, earlierMid));
        when(seniorityCalculator.compute(userId, careerId)).thenReturn(
                new SeniorityCalculator.SeniorityVerdict(
                        SeniorityLevel.FRESHER, SeniorityLevel.FRESHER,
                        new BigDecimal("0.43"), new BigDecimal("0.29"), 14, 6, 4));

        StudentLevelService service = new StudentLevelServiceImpl(
                authenticatedStudentService, studentRepository, assessmentRepository,
                seniorityCalculator, new StudentAssessmentMapper());

        assertEquals("MID", service.currentLevel().orElseThrow().getLevel());
    }
}
