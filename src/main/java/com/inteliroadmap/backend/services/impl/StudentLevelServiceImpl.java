package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.components.SeniorityCalculator;
import com.inteliroadmap.backend.domain.dto.response.student.StudentLevelResponse;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.StudentAssessment;
import com.inteliroadmap.backend.domain.enums.SeniorityLevel;
import com.inteliroadmap.backend.mappers.StudentAssessmentMapper;
import com.inteliroadmap.backend.repositories.StudentAssessmentRepository;
import com.inteliroadmap.backend.repositories.StudentRepository;
import com.inteliroadmap.backend.services.AuthenticatedStudentService;
import com.inteliroadmap.backend.services.StudentLevelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.Comparator;
import java.util.List;

/**
 * Implementation of {@link StudentLevelService}.
 *
 * <p>Every path returns {@link Optional#empty()} rather than throwing. The level
 * is an enhancement layered on three existing features; a student who never took
 * the assessment, a career with no required skills on file, or a transient
 * database problem must all degrade to "no level" and leave those features
 * working exactly as they did before.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StudentLevelServiceImpl implements StudentLevelService {

    static final String COMPLETED_STATUS = "COMPLETED";

    private final AuthenticatedStudentService authenticatedStudentService;
    private final StudentRepository studentRepository;
    private final StudentAssessmentRepository studentAssessmentRepository;
    private final SeniorityCalculator seniorityCalculator;
    private final StudentAssessmentMapper studentAssessmentMapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<StudentLevelResponse> currentLevel() {
        try {
            Student student = authenticatedStudentService.getRequiredStudent();
            return resolve(student);
        } catch (Exception e) {
            log.debug("StudentLevelServiceImpl: no level for the current student: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudentLevelResponse> levelOf(UUID userId) {
        if (userId == null) return Optional.empty();
        try {
            return studentRepository.findById(userId).flatMap(this::resolve);
        } catch (Exception e) {
            log.debug("StudentLevelServiceImpl: no level for student {}: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<StudentLevelResponse> resolve(Student student) {
        if (student == null || student.getUserId() == null) return Optional.empty();

        // No completed run means the student skipped the assessment. That is a
        // deliberate absence, not a missing default — do not invent FRESHER.
        UUID careerId = student.getCareerRole() == null ? null : student.getCareerRole().getCareerId();
        // The level is only meaningful against a target. A student who changed
        // career to one with no data on file has no comparable level any more.
        if (careerId == null) return Optional.empty();

        List<StudentAssessment> completed = studentAssessmentRepository
                .findByUserIdAndCareerIdAndStatusOrderByCreatedAtDesc(
                        student.getUserId(), careerId, COMPLETED_STATUS);
        if (completed.isEmpty()) return Optional.empty();

        StudentAssessment bestAssessment = completed.stream()
                .filter(row -> row.getAiLevel() != null)
                .max(Comparator.comparingInt(row -> levelRank(row.getAiLevel())))
                .orElse(completed.getFirst());

        SeniorityCalculator.SeniorityVerdict coverageVerdict =
                seniorityCalculator.compute(student.getUserId(), careerId);
        if (coverageVerdict.requiredCount() == 0) return Optional.empty();

        // The paper and the live coverage calculator measure complementary
        // evidence. The old path discarded the paper's result entirely: a MID
        // paper could become FRESHER because the career's HIGH catalog contained
        // languages and tools the paper never asked about. Keep the completed
        // assessment as the floor; repositories, transcripts and mentor evidence
        // can still raise the live level afterwards.
        SeniorityLevel effectiveLevel = SeniorityLevel.max(
                bestAssessment.getAiLevel(), coverageVerdict.level());
        SeniorityCalculator.SeniorityVerdict verdict = new SeniorityCalculator.SeniorityVerdict(
                effectiveLevel,
                SeniorityLevel.max(bestAssessment.getAiRawLevel(), coverageVerdict.rawLevel()),
                coverageVerdict.ratioAll(),
                coverageVerdict.ratioVerified(),
                coverageVerdict.requiredCount(),
                coverageVerdict.heldCount(),
                coverageVerdict.verifiedCount());

        return Optional.of(studentAssessmentMapper.toLevelResponse(verdict, bestAssessment));
    }

    private int levelRank(SeniorityLevel level) {
        if (level == null || level == SeniorityLevel.UNKNOWN) return -1;
        for (int i = 0; i < SeniorityLevel.LADDER.length; i++) {
            if (SeniorityLevel.LADDER[i] == level) return i;
        }
        return -1;
    }
}
