package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.ai.SkillMatch;
import com.inteliroadmap.backend.ai.analyzer.SkillAssessmentAiAnalyzer;
import com.inteliroadmap.backend.ai.analyzer.SkillAssessmentAiAnalyzer.AiAssessmentVerdict;
import com.inteliroadmap.backend.ai.analyzer.SkillAssessmentAiAnalyzer.AssessedSkill;
import com.inteliroadmap.backend.components.AssessmentQuestionBuilder;
import com.inteliroadmap.backend.components.SeniorityCalculator;
import com.inteliroadmap.backend.components.SkillProficiencyPromoter;
import com.inteliroadmap.backend.components.SeniorityCalculator.SeniorityVerdict;
import com.inteliroadmap.backend.domain.dto.request.AssessmentAnswerRequest;
import com.inteliroadmap.backend.domain.dto.request.SubmitAssessmentRequest;
import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapRecommendationDecisionResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapRecommendationResponse;
import com.inteliroadmap.backend.domain.dto.response.student.AssessedSkillResponse;
import com.inteliroadmap.backend.domain.dto.response.student.AssessmentQuestionSetResponse;
import com.inteliroadmap.backend.domain.dto.response.student.StudentAssessmentResultResponse;
import com.inteliroadmap.backend.domain.entity.CareerRole;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.StudentAssessment;
import com.inteliroadmap.backend.domain.entity.StudentSkill;
import com.inteliroadmap.backend.domain.enums.ProficiencyLevel;
import com.inteliroadmap.backend.domain.enums.SeniorityLevel;
import com.inteliroadmap.backend.domain.model.AssessmentQuestion;
import com.inteliroadmap.backend.exceptions.BadRequestException;
import com.inteliroadmap.backend.mappers.StudentAssessmentMapper;
import com.inteliroadmap.backend.repositories.SkillRepository;
import com.inteliroadmap.backend.repositories.StudentAssessmentRepository;
import com.inteliroadmap.backend.repositories.StudentSkillRepository;
import com.inteliroadmap.backend.services.AuthenticatedStudentService;
import com.inteliroadmap.backend.services.RoadmapPersonalizationService;
import com.inteliroadmap.backend.services.SkillEvidenceService;
import com.inteliroadmap.backend.services.StudentAssessmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of {@link StudentAssessmentService}.
 *
 * <p><b>Transactions.</b> {@link #submitAssessment} is deliberately <i>not</i>
 * annotated, unlike most service methods here. It spans a model call that takes
 * seconds, and holding a database transaction open across that would pin a
 * connection for the duration of every concurrent onboarding. Each step instead
 * runs inside its own collaborator's transaction, and the assessment row is
 * written before the model is called so a timeout still leaves a record of the
 * attempt rather than losing the student's answers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StudentAssessmentServiceImpl implements StudentAssessmentService {

    private static final String COMPLETED_STATUS = "COMPLETED";

    /**
     * Confidence ceilings by the level the grading settled on.
     *
     * <p>These sit under the floors the personalization engine applies to
     * important skills, which is what keeps an auto-applied questionnaire from
     * clearing a foundational node. See {@code SkillEvidenceServiceImpl}.
     */
    private static final double CONFIDENCE_CAP_PRACTICED = 0.60;
    private static final double CONFIDENCE_CAP_APPLIED = 0.75;
    private static final double CONFIDENCE_CAP_PROFESSIONAL = 0.80;

    /** Fallback confidence when the model is unavailable — plain self-report. */
    private static final double SELF_REPORT_CONFIDENCE = 0.60;

    /** Shortest note that can plausibly describe a project rather than dodge the question. */
    private static final int MIN_NOTE_LENGTH = 30;

    private final AuthenticatedStudentService authenticatedStudentService;
    private final AssessmentQuestionBuilder assessmentQuestionBuilder;
    private final SeniorityCalculator seniorityCalculator;
    private final SkillAssessmentAiAnalyzer skillAssessmentAiAnalyzer;
    private final SkillEvidenceService skillEvidenceService;
    private final SkillProficiencyPromoter skillProficiencyPromoter;
    private final RoadmapPersonalizationService roadmapPersonalizationService;
    private final StudentAssessmentRepository studentAssessmentRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final SkillRepository skillRepository;
    private final StudentAssessmentMapper studentAssessmentMapper;

    @Value("${spring.ai.openai.chat.options.model:unknown}")
    private String modelName;

    // ── Questions ───────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public AssessmentQuestionSetResponse getQuestionSet() {
        Student student = authenticatedStudentService.getRequiredStudent();
        CareerRole career = requireCareer(student);

        List<AssessmentQuestion> questions =
                assessmentQuestionBuilder.build(career.getCareerId(), student.getUserId());

        return studentAssessmentMapper.toQuestionSetResponse(
                career.getCareerId(),
                career.getCareerName(),
                questions,
                questions.isEmpty()
                        ? "We do not have enough skill data for this career yet, so there is nothing to assess."
                        : null);
    }

    // ── Submit ──────────────────────────────────────────────────────────────

    @Override
    public StudentAssessmentResultResponse submitAssessment(SubmitAssessmentRequest request) {
        Student student = authenticatedStudentService.getRequiredStudent();
        CareerRole career = requireCareer(student);
        UUID userId = student.getUserId();

        List<AssessmentQuestion> questions =
                assessmentQuestionBuilder.build(career.getCareerId(), userId);
        if (questions.isEmpty()) {
            throw new BadRequestException(
                    "This career has no skill data yet, so the assessment cannot be scored.");
        }

        Map<UUID, AssessmentQuestion> questionById = new LinkedHashMap<>();
        questions.forEach(q -> questionById.put(q.skillId(), q));

        List<Answer> answers = validateAnswers(request, questionById);

        // Persisted before the model is called: if the call times out, the run is
        // still on record with the student's answers instead of vanishing.
        StudentAssessment assessment = studentAssessmentRepository.save(StudentAssessment.builder()
                .userId(userId)
                .careerId(career.getCareerId())
                .questions(questionsAsJson(questions))
                .answers(answersAsJson(answers))
                .status(COMPLETED_STATUS)
                .modelUsed(modelName)
                .build());

        // ── The slow part, outside any transaction of ours ──────────────────
        AiAssessmentVerdict verdict = skillAssessmentAiAnalyzer.evaluate(
                career.getCareerName(),
                questions.stream().map(AssessmentQuestion::skillName).toList(),
                renderAnswers(answers));

        Map<UUID, Graded> graded = reconcile(answers, verdict);

        upsertStudentSkills(userId, graded);
        List<UUID> evidenceIds = skillEvidenceService.recordAssessmentEvidence(
                userId, toSkillMatches(graded), assessment.getAssessmentId());
        // Weighs the claims and, just as importantly, settles them. Without this the
        // assessment's rows stayed PENDING for good: the only code that accepted
        // evidence was the personalization engine, and it only ever closed rows tied
        // to a node it had just completed. Safe for a questionnaire because the
        // promoter refuses to set verified_by from a MANUAL source, so this raises
        // proficiency without ever lifting the JUNIOR ceiling.
        try {
            skillProficiencyPromoter.promoteFromEvidence(userId, evidenceIds);
        } catch (Exception e) {
            log.warn("StudentAssessmentServiceImpl: could not promote from {} assessment evidence "
                    + "row(s) for user {}: {}", evidenceIds.size(), userId, e.getMessage());
        }

        SeniorityVerdict seniority = seniorityCalculator.compute(userId, career.getCareerId());
        SeniorityLevel finalLevel = SeniorityLevel.min(seniority.level(), parseLevel(verdict.level()));

        assessment.setAiLevel(finalLevel);
        assessment.setAiRawLevel(parseLevel(verdict.level()));
        assessment.setAiRationale(verdict.rationale());
        assessment.setAiConfidence(round(verdict.overallConfidence()));
        assessment.setRatioAll(seniority.ratioAll());
        assessment.setRatioVerified(seniority.ratioVerified());
        assessment.setRequiredCount(seniority.requiredCount());
        // Personalization reads the latest persisted level to decide how far a
        // proven skill may propagate into its basic descendants. Persist the
        // verdict first; calling it while aiLevel is still null makes every
        // assessment auto-complete zero nodes.
        assessment.setComputedAt(LocalDateTime.now());
        studentAssessmentRepository.save(assessment);

        Applied applied = applyToRoadmap(userId);
        assessment.setAppliedNodeCount(applied.nodeCount());
        assessment.setRecommendationId(applied.recommendationId());
        studentAssessmentRepository.save(assessment);

        log.info("StudentAssessmentServiceImpl: graded user {} as {} (model said {}); "
                        + "{} node(s) marked as already covered.",
                userId, finalLevel, verdict.level(), applied.nodeCount());

        return studentAssessmentMapper.toResultResponse(
                assessment, career.getCareerName(), toAssessedSkillResponses(graded));
    }

    // ── Latest ──────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Optional<StudentAssessmentResultResponse> getLatestResult() {
        Student student = authenticatedStudentService.getRequiredStudent();
        return studentAssessmentRepository
                .findFirstByUserIdAndStatusOrderByCreatedAtDesc(student.getUserId(), COMPLETED_STATUS)
                .map(assessment -> studentAssessmentMapper.toResultResponse(
                        assessment,
                        student.getCareerRole() == null ? null : student.getCareerRole().getCareerName(),
                        List.of()));
    }

    // ── Internals ───────────────────────────────────────────────────────────

    /** One validated answer, paired with the question it responds to. */
    private record Answer(UUID skillId, String skillName, ProficiencyLevel declared, String note) {}

    /** An answer after the model has had its say. */
    private record Graded(UUID skillId, String skillName, ProficiencyLevel declared,
                          ProficiencyLevel assessed, double confidence, String justification) {}

    private record Applied(int nodeCount, UUID recommendationId) {}

    private CareerRole requireCareer(Student student) {
        CareerRole career = student.getCareerRole();
        if (career == null || career.getCareerId() == null) {
            throw new BadRequestException(
                    "Pick a target career before taking the assessment - the questions are matched to it.");
        }
        return career;
    }

    private List<Answer> validateAnswers(SubmitAssessmentRequest request,
                                         Map<UUID, AssessmentQuestion> questionById) {
        List<Answer> answers = new ArrayList<>();
        for (AssessmentAnswerRequest raw : request.getAnswers()) {
            AssessmentQuestion question = questionById.get(raw.getSkillId());
            if (question == null) {
                // Rejecting rather than ignoring: an answer to a question we did not
                // ask means the client is out of step with the server, and silently
                // dropping it would grade an assessment nobody actually sat.
                throw new BadRequestException(
                        "Answer refers to a skill that was not part of this assessment: " + raw.getSkillId());
            }

            ProficiencyLevel declared;
            try {
                declared = ProficiencyLevel.fromAnswer(raw.getLevel());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Unknown level '" + raw.getLevel()
                        + "'. Expected one of NONE, AWARE, PRACTICED, APPLIED, PROFESSIONAL.");
            }
            if (declared == null) continue; // NONE - the student does not have this skill.

            String note = raw.getNote() == null ? null : raw.getNote().trim();
            boolean claimsExperience = declared.getScore() >= ProficiencyLevel.APPLIED.getScore();
            if (question.noteRequired() && claimsExperience
                    && (note == null || note.length() < MIN_NOTE_LENGTH)) {
                throw new BadRequestException("Describe what you built with "
                        + question.skillName() + " (at least " + MIN_NOTE_LENGTH + " characters).");
            }

            answers.add(new Answer(question.skillId(), question.skillName(), declared, note));
        }

        if (answers.isEmpty()) {
            throw new BadRequestException("Answer at least one question before submitting.");
        }
        return answers;
    }

    /**
     * Pairs each answer with the model's verdict on it.
     *
     * <p>A skill the model did not return — including every skill when the model
     * was unavailable — falls back to what the student said, at plain self-report
     * confidence. The student is never punished for an outage; they simply do not
     * get the benefit of the doubt on anything.
     */
    private Map<UUID, Graded> reconcile(List<Answer> answers, AiAssessmentVerdict verdict) {
        Map<String, AssessedSkill> byName = new HashMap<>();
        if (verdict.assessedSkills() != null) {
            for (AssessedSkill assessed : verdict.assessedSkills()) {
                if (assessed != null && assessed.skill() != null) {
                    byName.put(assessed.skill().trim().toLowerCase(), assessed);
                }
            }
        }

        Map<UUID, Graded> graded = new LinkedHashMap<>();
        for (Answer answer : answers) {
            AssessedSkill assessed = byName.get(answer.skillName().toLowerCase());
            if (assessed == null) {
                graded.put(answer.skillId(), new Graded(answer.skillId(), answer.skillName(),
                        answer.declared(), answer.declared(), SELF_REPORT_CONFIDENCE, null));
                continue;
            }

            ProficiencyLevel modelLevel = ProficiencyLevel.fromScore(assessed.level());
            // The model is instructed never to grade above the student's own claim;
            // enforce it here too rather than trusting the instruction.
            ProficiencyLevel finalLevel = (modelLevel == null
                    || modelLevel.getScore() > answer.declared().getScore())
                    ? answer.declared()
                    : modelLevel;

            graded.put(answer.skillId(), new Graded(answer.skillId(), answer.skillName(),
                    answer.declared(), finalLevel,
                    capConfidence(finalLevel, assessed.confidence()),
                    assessed.justification()));
        }
        return graded;
    }

    /** Clamps confidence to what the graded level can justify. */
    private double capConfidence(ProficiencyLevel level, double raw) {
        double value = Math.max(0.0, raw);
        return switch (level) {
            case PROFESSIONAL -> Math.min(value, CONFIDENCE_CAP_PROFESSIONAL);
            case APPLIED -> Math.min(value, CONFIDENCE_CAP_APPLIED);
            default -> Math.min(value, CONFIDENCE_CAP_PRACTICED);
        };
    }

    /**
     * Writes the graded levels onto the student's skill rows, creating rows that
     * do not exist yet.
     *
     * <p>A row already verified by a transcript, a repository or a mentor is left
     * alone: an objective source outranks the student's own account of themselves,
     * and a re-taken assessment must not be able to talk a verified level down.
     */
    private void upsertStudentSkills(UUID userId, Map<UUID, Graded> graded) {
        if (graded.isEmpty()) return;

        Map<UUID, StudentSkill> existing = new HashMap<>();
        for (StudentSkill ss : studentSkillRepository.findByStudent_UserId(userId)) {
            if (ss.getSkill() != null && ss.getSkill().getSkillId() != null) {
                existing.put(ss.getSkill().getSkillId(), ss);
            }
        }

        Map<UUID, Skill> skills = new HashMap<>();
        skillRepository.findAllById(graded.keySet())
                .forEach(skill -> skills.put(skill.getSkillId(), skill));

        List<StudentSkill> toSave = new ArrayList<>();
        for (Graded g : graded.values()) {
            // AWARE means "I have read about it" — not a skill the student has.
            // Recording it would inflate every coverage figure downstream.
            if (g.assessed() == null || g.assessed().getScore() < ProficiencyLevel.PRACTICED.getScore()) {
                continue;
            }

            StudentSkill row = existing.get(g.skillId());
            if (row == null) {
                Skill skill = skills.get(g.skillId());
                if (skill == null) continue;
                row = StudentSkill.builder()
                        .student(Student.builder().userId(userId).build())
                        .skill(skill)
                        .build();
            } else if (row.getVerifiedBy() != null && !row.getVerifiedBy().isBlank()) {
                continue;
            }

            row.setProficiency((short) g.assessed().getScore());
            row.setSelfDeclared(true);
            row.setVerifiedBy(null);
            toSave.add(row);
        }

        if (!toSave.isEmpty()) {
            studentSkillRepository.saveAll(toSave);
            log.debug("StudentAssessmentServiceImpl: wrote proficiency onto {} skill row(s) for user {}.",
                    toSave.size(), userId);
        }
    }

    /**
     * Marks the roadmap nodes the student's evidence covers.
     *
     * <p>Runs through the existing recommendation engine and then accepts what it
     * produced, rather than writing progress directly. The student is not asked —
     * that is the product decision — but everything the engine gates on still
     * applies (a node must allow evidence, and the evidence must clear that node's
     * proficiency bar), and the accepted recommendation remains as the record of
     * what was changed and why.
     */
    private Applied applyToRoadmap(UUID userId) {
        try {
            List<RoadmapRecommendationResponse> generated =
                    roadmapPersonalizationService.generateRecommendationsForCurrentStudent();
            if (generated == null || generated.isEmpty()) {
                return new Applied(0, null);
            }

            int nodeCount = 0;
            UUID recommendationId = null;
            for (RoadmapRecommendationResponse recommendation : generated) {
                if (recommendation == null || recommendation.getRecommendationId() == null) continue;
                // Count what acceptance actually marked, not what was offered.
                // Items include suggestions that never write progress, and gating
                // can reject the rest, so the item count told the student "12 nodes
                // marked" when the true answer was zero.
                RoadmapRecommendationDecisionResponse decision =
                        roadmapPersonalizationService.acceptRecommendation(recommendation.getRecommendationId());
                nodeCount += decision == null || decision.getCompletedNodeCount() == null
                        ? 0 : decision.getCompletedNodeCount();
                recommendationId = recommendation.getRecommendationId();
            }
            return new Applied(nodeCount, recommendationId);
        } catch (Exception e) {
            // The grade is worth showing even if the roadmap could not be updated;
            // failing the whole submission here would throw away a completed form.
            log.error("StudentAssessmentServiceImpl: could not apply the assessment to the roadmap "
                    + "for user {}; the level was still recorded.", userId, e);
            return new Applied(0, null);
        }
    }

    private List<SkillMatch> toSkillMatches(Map<UUID, Graded> graded) {
        List<SkillMatch> matches = new ArrayList<>();
        for (Graded g : graded.values()) {
            if (g.assessed() == null || g.assessed().getScore() < ProficiencyLevel.PRACTICED.getScore()) {
                continue;
            }
            matches.add(new SkillMatch(g.skillName(), g.confidence()));
        }
        return matches;
    }

    private List<AssessedSkillResponse> toAssessedSkillResponses(Map<UUID, Graded> graded) {
        return graded.values().stream()
                .map(g -> studentAssessmentMapper.toAssessedSkillResponse(
                        g.skillId(), g.skillName(), g.declared(), g.assessed(),
                        g.confidence(), g.justification()))
                .toList();
    }

    private String renderAnswers(List<Answer> answers) {
        StringBuilder sb = new StringBuilder();
        for (Answer a : answers) {
            sb.append("- ").append(a.skillName()).append(": ").append(a.declared().name());
            if (a.note() != null && !a.note().isBlank()) {
                sb.append(" - ").append(a.note());
            } else {
                sb.append(" - (no explanation given)");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private List<Map<String, Object>> questionsAsJson(List<AssessmentQuestion> questions) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AssessmentQuestion q : questions) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("skillId", String.valueOf(q.skillId()));
            row.put("skillName", q.skillName());
            row.put("importance", q.importance() == null ? null : q.importance().name());
            row.put("noteRequired", q.noteRequired());
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> answersAsJson(List<Answer> answers) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Answer a : answers) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("skillId", String.valueOf(a.skillId()));
            row.put("skillName", a.skillName());
            row.put("level", a.declared().name());
            row.put("note", a.note());
            out.add(row);
        }
        return out;
    }

    private SeniorityLevel parseLevel(String raw) {
        try {
            SeniorityLevel level = SeniorityLevel.fromString(raw);
            return level == SeniorityLevel.UNKNOWN ? null : level;
        } catch (IllegalArgumentException e) {
            log.warn("StudentAssessmentServiceImpl: model returned an unrecognised level '{}'; ignoring it.", raw);
            return null;
        }
    }

    private BigDecimal round(double value) {
        return BigDecimal.valueOf(Math.max(0.0, value)).setScale(2, RoundingMode.HALF_UP);
    }
}
