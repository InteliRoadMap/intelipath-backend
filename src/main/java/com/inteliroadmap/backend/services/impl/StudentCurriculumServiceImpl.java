package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.request.SubjectEntry;
import com.inteliroadmap.backend.domain.dto.response.roadmap.CloResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.MaterialResponse;

import com.inteliroadmap.backend.domain.dto.request.DeclareCurriculumTermRequest;
import com.inteliroadmap.backend.domain.dto.request.SetStudentCurriculumRequest;
import com.inteliroadmap.backend.domain.dto.request.UpdateFptSubjectsRequest;
import com.inteliroadmap.backend.domain.dto.response.roadmap.CurriculumOptionResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.FptSubjectDetailResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.FptSubjectResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.StudentCurriculumResponse;
import com.inteliroadmap.backend.domain.entity.FptCurriculum;
import com.inteliroadmap.backend.domain.entity.FptCurriculumSubject;
import com.inteliroadmap.backend.domain.entity.FptSubject;
import com.inteliroadmap.backend.domain.entity.FptSubjectResource;
import com.inteliroadmap.backend.domain.entity.FptSubjectSkill;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.StudentFptSubject;
import com.inteliroadmap.backend.domain.entity.StudentSkillEvidence;
import com.inteliroadmap.backend.domain.enums.FptResourceKind;
import com.inteliroadmap.backend.domain.enums.EvidenceStatus;
import com.inteliroadmap.backend.components.RoadmapRefreshTrigger;
import com.inteliroadmap.backend.domain.enums.EvidenceType;
import com.inteliroadmap.backend.domain.enums.StudentSubjectSource;
import com.inteliroadmap.backend.domain.enums.StudentSubjectStatus;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.repositories.FptCurriculumRepository;
import com.inteliroadmap.backend.repositories.FptCurriculumSubjectRepository;
import com.inteliroadmap.backend.repositories.FptSubjectCloRepository;
import com.inteliroadmap.backend.repositories.FptSubjectResourceRepository;
import com.inteliroadmap.backend.repositories.FptSubjectRepository;
import com.inteliroadmap.backend.repositories.FptSubjectSkillRepository;
import com.inteliroadmap.backend.repositories.StudentFptSubjectRepository;
import com.inteliroadmap.backend.repositories.StudentRepository;
import com.inteliroadmap.backend.repositories.StudentSkillEvidenceRepository;
import com.inteliroadmap.backend.services.AuthenticatedStudentService;
import com.inteliroadmap.backend.services.StudentCurriculumService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of {@link StudentCurriculumService}. Multi-curriculum aware: a student
 * follows one {@link FptCurriculum} version (auto-matched from their admission year and
 * major, overridable), and their checklist + term inference read that curriculum's
 * per-term subject mapping. Subject facts and skill coverage are shared/deduplicated
 * across curricula. FLM transcript evidence is rebuilt from scratch on every change
 * (one row per subject x skill), so un-ticking a subject cleanly retracts its evidence;
 * student_progress is never touched here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StudentCurriculumServiceImpl implements StudentCurriculumService {

    /** detectedBy prefix marking evidence this service owns. */
    private static final String FLM_PREFIX = "FLM:";

    /**
     * How much a passed subject is worth as evidence, by how many passed subjects teach the
     * same skill.
     *
     * <p>These land deliberately on the thresholds the roadmap actually checks
     * (RoadmapPersonalizationServiceImpl: LOW 0.60, AVG 0.70, HIGH 0.85). The previous
     * ladder drifted 0.72 / 0.78 / 0.84 / 0.90 in fixed 0.06 steps and stepped straight
     * over 0.85, so a HIGH-importance skill — which is what every auto-completable Frontend
     * node is — needed four separate subjects to unlock while three counted for nothing.
     * A student who declared five terms unlocked zero nodes.
     *
     * <p>The rule now reads: one subject is ordinary evidence, two independent subjects
     * covering the same skill are enough even for a core skill, three or more is as strong
     * as this source ever gets.
     */
    private static final BigDecimal[] CONFIDENCE_BY_COVERAGE = {
            new BigDecimal("0.75"), // 1 subject
            new BigDecimal("0.85"), // 2 subjects — clears the HIGH-importance floor
            new BigDecimal("0.90"), // 3+ subjects
    };
    /** FPT cohort K = admission year − 2004 (2023 → K19, 2024 → K20). */
    private static final int COHORT_BASE_YEAR = 2004;

    private final AuthenticatedStudentService authenticatedStudentService;
    private final RoadmapRefreshTrigger roadmapRefreshTrigger;
    private final StudentRepository studentRepository;
    private final FptSubjectRepository fptSubjectRepository;
    private final FptSubjectSkillRepository fptSubjectSkillRepository;
    private final FptCurriculumRepository fptCurriculumRepository;
    private final FptCurriculumSubjectRepository fptCurriculumSubjectRepository;
    private final StudentFptSubjectRepository studentFptSubjectRepository;
    private final StudentSkillEvidenceRepository evidenceRepository;
    private final FptSubjectCloRepository fptSubjectCloRepository;
    private final FptSubjectResourceRepository fptSubjectResourceRepository;

    @Override
    @Transactional
    public StudentCurriculumResponse getCurriculum() {
        Student student = authenticatedStudentService.getRequiredStudent();
        FptCurriculum curriculum = resolveCurriculum(student);
        return buildCurriculumResponse(student, curriculum);
    }

    @Override
    @Transactional(readOnly = true)
    public FptSubjectDetailResponse getSubjectDetail(String subjectCode) {
        String code = subjectCode == null ? "" : subjectCode.trim();
        FptSubject subject = fptSubjectRepository.findById(code)
                .orElseThrow(() -> new ResourceNotFoundException("Subject not found: " + code));

        List<String> skills = fptSubjectSkillRepository.findBySubjectCodeIn(List.of(code)).stream()
                .map(FptSubjectSkill::getSkillName)
                .toList();

        List<CloResponse> clos =
                fptSubjectCloRepository.findBySubjectCodeOrderByOrderIndexAsc(code).stream()
                        .map(c -> CloResponse.builder()
                                .code(c.getCode())
                                .outcome(c.getOutcome())
                                .build())
                        .toList();

        // Split by kind rather than making the page do it: MATERIAL rows are references
        // with nothing to download, SESSION rows are where files live.
        Map<FptResourceKind, List<MaterialResponse>> byKind =
                fptSubjectResourceRepository
                        .findBySubjectCodeInOrderBySubjectCodeAscOrderIndexAsc(List.of(code)).stream()
                        .collect(Collectors.groupingBy(
                                FptSubjectResource::getKind,
                                Collectors.mapping(StudentCurriculumServiceImpl::toMaterial, Collectors.toList())));

        return FptSubjectDetailResponse.builder()
                .code(subject.getCode())
                .name(subject.getName())
                .credits(subject.getCredits())
                .prerequisite(subject.getPrerequisite())
                .description(subject.getDescription())
                .skills(skills)
                .clos(clos)
                .materials(byKind.getOrDefault(FptResourceKind.MATERIAL, List.of()))
                .sessions(byKind.getOrDefault(FptResourceKind.SESSION, List.of()))
                .build();
    }

    /** Never exposes sourceUrl or storagePath: downloads go through the signed-URL endpoint. */
    private static MaterialResponse toMaterial(FptSubjectResource r) {
        return MaterialResponse.builder()
                .id(r.getId())
                .title(r.getTitle())
                .topic(r.getTopic())
                .cloRef(r.getCloRef())
                .url(r.getUrl())
                .sizeBytes(r.getSizeBytes())
                .downloadable(r.getStoragePath() != null && !r.getStoragePath().isBlank())
                .build();
    }

    @Override
    @Transactional
    public StudentCurriculumResponse applyCurriculumTerm(DeclareCurriculumTermRequest request) {
        Student student = authenticatedStudentService.getRequiredStudent();
        UUID userId = student.getUserId();
        FptCurriculum curriculum = requireCurriculum(student);
        int term = request.getCompletedTerm();

        List<FptCurriculumSubject> upToTerm = fptCurriculumSubjectRepository
                .findForStudentUpToTerm(curriculum.getId(), term, student.getFptComboCode());
        int marked = 0;
        for (FptCurriculumSubject cs : upToTerm) {
            StudentFptSubject existing = studentFptSubjectRepository
                    .findByUserIdAndSubjectCode(userId, cs.getSubjectCode()).orElse(null);
            // A manual tick is the student's explicit intent; don't overwrite it.
            if (existing != null && existing.getSource() == StudentSubjectSource.MANUAL) {
                continue;
            }
            upsertSubject(userId, cs.getSubjectCode(), curriculum.getId(),
                    StudentSubjectSource.CURRICULUM_TERM, existing);
            marked++;
        }

        log.info("StudentCurriculumService: user {} declared term {} on {} -> {} subjects marked PASSED",
                userId, term, curriculum.getCode(), marked);
        syncEvidenceFromPassedSubjects(userId);
        return buildCurriculumResponse(student, curriculum);
    }

    @Override
    @Transactional
    public StudentCurriculumResponse updateSubjects(UpdateFptSubjectsRequest request) {
        Student student = authenticatedStudentService.getRequiredStudent();
        UUID userId = student.getUserId();
        FptCurriculum curriculum = resolveCurriculum(student);
        UUID curriculumId = curriculum != null ? curriculum.getId() : null;

        for (SubjectEntry entry : request.getSubjects()) {
            String code = entry.getSubjectCode().trim();
            if (code.isEmpty() || !fptSubjectRepository.existsById(code)) {
                continue;
            }
            StudentFptSubject existing = studentFptSubjectRepository
                    .findByUserIdAndSubjectCode(userId, code).orElse(null);
            if (entry.isPassed()) {
                upsertSubject(userId, code, curriculumId, StudentSubjectSource.MANUAL, existing);
            } else if (existing != null) {
                studentFptSubjectRepository.delete(existing);
            }
        }

        syncEvidenceFromPassedSubjects(userId);
        return buildCurriculumResponse(student, curriculum);
    }

    @Override
    @Transactional
    public StudentCurriculumResponse setCurriculum(SetStudentCurriculumRequest request) {
        Student student = authenticatedStudentService.getRequiredStudent();
        FptCurriculum curriculum = fptCurriculumRepository.findById(request.getCurriculumId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Curriculum not found: " + request.getCurriculumId()));
        student.setFptCurriculumId(curriculum.getId());
        studentRepository.save(student);
        log.info("StudentCurriculumService: user {} set curriculum -> {}", student.getUserId(), curriculum.getCode());
        return buildCurriculumResponse(student, curriculum);
    }

    /** Insert or update a PASSED declaration for one subject. */
    private void upsertSubject(UUID userId, String code, UUID curriculumId,
                               StudentSubjectSource source, StudentFptSubject existing) {
        StudentFptSubject record = existing != null ? existing : StudentFptSubject.builder()
                .userId(userId)
                .subjectCode(code)
                .build();
        record.setCurriculumId(curriculumId);
        record.setStatus(StudentSubjectStatus.PASSED);
        record.setSource(source);
        studentFptSubjectRepository.save(record);
    }

    /**
     * Resolve which curriculum the student follows: their explicit choice if set, else
     * auto-matched from admission year (cohort) + major (program), else the default. The
     * resolved choice is persisted so it is stable on later reads.
     */
    private FptCurriculum resolveCurriculum(Student student) {
        if (student.getFptCurriculumId() != null) {
            FptCurriculum chosen = fptCurriculumRepository.findById(student.getFptCurriculumId()).orElse(null);
            if (chosen != null) {
                return chosen;
            }
        }
        FptCurriculum derived = deriveCurriculum(student);
        if (derived != null && !derived.getId().equals(student.getFptCurriculumId())) {
            student.setFptCurriculumId(derived.getId());
            studentRepository.save(student);
        }
        return derived;
    }

    private FptCurriculum requireCurriculum(Student student) {
        FptCurriculum curriculum = resolveCurriculum(student);
        if (curriculum == null) {
            throw new ResourceNotFoundException("No FPT curriculum is available to declare against.");
        }
        return curriculum;
    }

    /** Auto-match a curriculum from the student's cohort (admission year) and program (major). */
    private FptCurriculum deriveCurriculum(Student student) {
        Integer cohort = student.getAdmissionDate() != null
                ? student.getAdmissionDate().getYear() - COHORT_BASE_YEAR : null;
        String program = programFromMajor(student.getMajor());
        if (program != null && cohort != null) {
            List<FptCurriculum> matches = fptCurriculumRepository
                    .findByProgramIgnoreCaseAndCohortOrderByEffectiveDateDescCodeAsc(program, cohort);
            if (!matches.isEmpty()) {
                return matches.get(0);
            }
        }
        return fptCurriculumRepository.findFirstByIsDefaultTrue().orElse(null);
    }

    /** Map a free-text major to a program slug. SE-focused product: defaults to "SE". */
    private static String programFromMajor(String major) {
        if (major == null || major.isBlank()) {
            return "SE";
        }
        String m = major.toLowerCase();
        if (m.contains("inform") && m.contains("assur")) return "IA";
        if (m.contains("artificial") || m.equals("ai")) return "AI";
        // Software engineering and anything else map to SE for now.
        return "SE";
    }

    /**
     * Rebuild all FLM transcript evidence for the student from their current PASSED
     * subjects. PENDING FLM rows are wiped and regenerated (so un-ticked subjects lose
     * their evidence); ACCEPTED FLM rows are preserved (that knowledge is already
     * applied) and never duplicated.
     */
    private void syncEvidenceFromPassedSubjects(UUID userId) {
        List<StudentFptSubject> passed = studentFptSubjectRepository.findByUserId(userId).stream()
                .filter(s -> s.getStatus() == StudentSubjectStatus.PASSED)
                .toList();

        List<StudentSkillEvidence> existing = evidenceRepository
                .findByUserIdAndDetectedByStartingWith(userId, FLM_PREFIX);
        List<StudentSkillEvidence> stalePending = existing.stream()
                .filter(e -> e.getStatus() == EvidenceStatus.PENDING)
                .toList();
        evidenceRepository.deleteAll(stalePending);

        Set<String> acceptedKeys = existing.stream()
                .filter(e -> e.getStatus() != EvidenceStatus.PENDING)
                .map(e -> e.getDetectedBy() + "|" + lower(e.getSkillName()))
                .collect(Collectors.toSet());

        if (passed.isEmpty()) {
            log.info("StudentCurriculumService: user {} has no PASSED subjects; FLM evidence cleared", userId);
            return;
        }

        List<String> passedCodes = passed.stream().map(StudentFptSubject::getSubjectCode).toList();
        List<FptSubjectSkill> links = fptSubjectSkillRepository.findBySubjectCodeIn(passedCodes);

        // How many passed subjects corroborate each skill -> confidence scaling.
        Map<String, Set<String>> subjectsBySkill = new HashMap<>();
        for (FptSubjectSkill link : links) {
            subjectsBySkill
                    .computeIfAbsent(lower(link.getSkillName()), k -> new HashSet<>())
                    .add(link.getSubjectCode());
        }

        List<StudentSkillEvidence> toCreate = new ArrayList<>();
        for (FptSubjectSkill link : links) {
            String detectedBy = FLM_PREFIX + link.getSubjectCode();
            String key = detectedBy + "|" + lower(link.getSkillName());
            if (acceptedKeys.contains(key)) {
                continue; // already accepted; don't re-create
            }
            int covering = subjectsBySkill.getOrDefault(lower(link.getSkillName()), Set.of()).size();
            toCreate.add(StudentSkillEvidence.builder()
                    .userId(userId)
                    .skillName(link.getSkillName())
                    .sourceType(EvidenceType.TRANSCRIPT)
                    .detectedBy(detectedBy)
                    .evidenceText("Covered by FPT subject " + link.getSubjectCode())
                    .confidence(confidenceFor(covering))
                    .status(EvidenceStatus.PENDING)
                    .detectedAt(LocalDateTime.now())
                    .build());
        }
        evidenceRepository.saveAll(toCreate);
        log.info("StudentCurriculumService: user {} FLM evidence rebuilt — {} PASSED subjects, {} evidence rows",
                userId, passed.size(), toCreate.size());

        // A transcript that changes nothing the student can see is not analysis,
        // it is filing. GitHub import already closed this loop; the transcript
        // path did not, so passing a subject left the roadmap untouched until the
        // student happened to press generate. The trigger swallows its own
        // failures, so this cannot break transcript submission.
        roadmapRefreshTrigger.refreshCurrentStudent("transcript");
    }

    /** See {@link #CONFIDENCE_BY_COVERAGE}: 1 subject 0.75, 2 subjects 0.85, 3 or more 0.90. */
    static BigDecimal confidenceFor(int coveringSubjects) {
        int index = Math.max(1, coveringSubjects) - 1;
        return CONFIDENCE_BY_COVERAGE[Math.min(index, CONFIDENCE_BY_COVERAGE.length - 1)];
    }

    /**
     * @param student the owner — scopes the subject list to their curriculum AND combo,
     *                so another specialisation's courses never leak into the response
     */
    private StudentCurriculumResponse buildCurriculumResponse(Student student, FptCurriculum curriculum) {
        UUID userId = student.getUserId();
        List<CurriculumOptionResponse> available = fptCurriculumRepository
                .findAllByOrderByProgramAscCohortDescCodeAsc().stream()
                .map(StudentCurriculumServiceImpl::toOption)
                .toList();

        if (curriculum == null) {
            return StudentCurriculumResponse.builder()
                    .completedTerm(null)
                    .availableCurricula(available)
                    .subjects(List.of())
                    .build();
        }

        // Term placement for THIS curriculum and this student's combo, ordered by
        // semester then code.
        List<FptCurriculumSubject> mapping = fptCurriculumSubjectRepository
                .findForStudent(curriculum.getId(), student.getFptComboCode());
        mapping.sort(Comparator
                .comparing((FptCurriculumSubject cs) -> cs.getSemester() == null ? Integer.MAX_VALUE : cs.getSemester())
                .thenComparing(FptCurriculumSubject::getSubjectCode));

        List<String> codes = mapping.stream().map(FptCurriculumSubject::getSubjectCode).toList();
        Map<String, FptSubject> subjectsByCode = fptSubjectRepository.findAllById(codes).stream()
                .collect(Collectors.toMap(FptSubject::getCode, s -> s, (a, b) -> a));

        Map<String, List<String>> skillsByCode = new HashMap<>();
        for (FptSubjectSkill link : fptSubjectSkillRepository.findBySubjectCodeIn(codes)) {
            skillsByCode.computeIfAbsent(link.getSubjectCode(), k -> new ArrayList<>()).add(link.getSkillName());
        }

        Map<String, StudentFptSubject> declaredByCode = studentFptSubjectRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(StudentFptSubject::getSubjectCode, s -> s, (a, b) -> a));

        Integer completedTerm = null;
        List<FptSubjectResponse> out = new ArrayList<>();
        for (FptCurriculumSubject cs : mapping) {
            String code = cs.getSubjectCode();
            FptSubject subject = subjectsByCode.get(code);
            if (subject == null) continue;
            StudentFptSubject declared = declaredByCode.get(code);
            if (declared != null && declared.getStatus() == StudentSubjectStatus.PASSED
                    && cs.getSemester() != null
                    && (completedTerm == null || cs.getSemester() > completedTerm)) {
                completedTerm = cs.getSemester();
            }
            out.add(FptSubjectResponse.builder()
                    .code(code)
                    .name(subject.getName())
                    .semester(cs.getSemester())
                    .credits(subject.getCredits())
                    .prerequisite(subject.getPrerequisite())
                    .skills(skillsByCode.getOrDefault(code, List.of()))
                    .status(declared != null ? declared.getStatus().name() : null)
                    .source(declared != null ? declared.getSource().name() : null)
                    .build());
        }

        return StudentCurriculumResponse.builder()
                .completedTerm(completedTerm)
                .curriculumId(curriculum.getId().toString())
                .curriculumCode(curriculum.getCode())
                .curriculumLabel(labelFor(curriculum))
                .availableCurricula(available)
                .subjects(out)
                .build();
    }

    private static CurriculumOptionResponse toOption(FptCurriculum c) {
        return CurriculumOptionResponse.builder()
                .id(c.getId().toString())
                .code(c.getCode())
                .program(c.getProgram())
                .cohort(c.getCohort())
                .batch(c.getBatch())
                .isDefault(c.isDefault())
                .build();
    }

    private static String labelFor(FptCurriculum c) {
        StringBuilder sb = new StringBuilder();
        if (c.getProgram() != null) sb.append(c.getProgram());
        if (c.getCohort() != null) {
            if (sb.length() > 0) sb.append(" • ");
            sb.append('K').append(c.getCohort());
            if (c.getBatch() != null) sb.append(c.getBatch());
        }
        return sb.length() > 0 ? sb.toString() : c.getCode();
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase();
    }
}
