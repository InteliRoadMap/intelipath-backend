package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.ai.analyzer.AssessmentRubricGrader;
import com.inteliroadmap.backend.ai.analyzer.AssessmentRubricGrader.RubricGrade;
import com.inteliroadmap.backend.components.AssessmentPaperLoader;
import com.inteliroadmap.backend.components.AssessmentPaperScorer;
import com.inteliroadmap.backend.components.AssessmentPaperScorer.ItemOutcome;
import com.inteliroadmap.backend.components.AssessmentPaperScorer.PaperVerdict;
import com.inteliroadmap.backend.components.AssessmentPaperScorer.SubmittedAnswer;
import com.inteliroadmap.backend.components.RoadmapRefreshTrigger;
import com.inteliroadmap.backend.components.SkillProficiencyPromoter;
import com.inteliroadmap.backend.domain.dto.ai.SkillMatch;
import com.inteliroadmap.backend.domain.dto.request.AssessmentItemAnswerRequest;
import com.inteliroadmap.backend.domain.dto.request.SubmitGradedAssessmentRequest;
import com.inteliroadmap.backend.domain.dto.response.student.AssessmentChoiceResponse;
import com.inteliroadmap.backend.domain.dto.response.student.AssessmentItemResponse;
import com.inteliroadmap.backend.domain.dto.response.student.GradedAssessmentPaperResponse;
import com.inteliroadmap.backend.domain.dto.response.student.GradedAssessmentResultResponse;
import com.inteliroadmap.backend.domain.dto.response.student.GradedItemResultResponse;
import com.inteliroadmap.backend.domain.entity.CareerRole;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.StudentAssessment;
import com.inteliroadmap.backend.domain.entity.StudentSkill;
import com.inteliroadmap.backend.domain.enums.ProficiencyLevel;
import com.inteliroadmap.backend.domain.model.AssessmentChoice;
import com.inteliroadmap.backend.domain.model.AssessmentItem;
import com.inteliroadmap.backend.domain.model.AssessmentPaper;
import com.inteliroadmap.backend.exceptions.BadRequestException;
import com.inteliroadmap.backend.repositories.SkillRepository;
import com.inteliroadmap.backend.repositories.StudentAssessmentRepository;
import com.inteliroadmap.backend.repositories.StudentSkillRepository;
import com.inteliroadmap.backend.services.AuthenticatedStudentService;
import com.inteliroadmap.backend.services.GradedAssessmentService;
import com.inteliroadmap.backend.services.SkillEvidenceService;
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
 * Serves and grades the real assessment.
 *
 * <h2>The order of operations is the interesting part</h2>
 *
 * <ol>
 *   <li>The paper is loaded from the classpath — never from the request. A client
 *       could otherwise post its own questions, or its own answer keys.</li>
 *   <li>The multiple choice is graded first, in code, and the row is persisted
 *       before the model is called. A timeout then costs the rubric half, not the
 *       whole sitting — the same reason the self-report path persists early.</li>
 *   <li>The model grades the written half against the bank's rubric.</li>
 *   <li>The two are blended and clamped by {@link AssessmentPaperScorer}, so a
 *       plausible essay cannot outvote the reproducible half by more than a band.</li>
 * </ol>
 *
 * <h2>Evidence, not just a number</h2>
 *
 * <p>Answering the caching questions correctly is evidence about caching, so the
 * per-skill verdict is written to {@code student_skills} and recorded as assessment
 * evidence — the same pipeline the self-report path uses, which means the roadmap
 * and the readiness figure move for the same reasons they always did. Confidence is
 * higher than a self-report's because the claim was checked against an answer key,
 * but it is still MANUAL-sourced evidence, so {@code SkillProficiencyPromoter}
 * refuses to mark it verified and the JUNIOR ceiling stays where it is.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GradedAssessmentServiceImpl implements GradedAssessmentService {

    private static final String COMPLETED_STATUS = "COMPLETED";

    /**
     * Confidence attached to a skill this paper evidenced.
     *
     * <p>Above the self-report's 0.60 because an answer key checked it, and well
     * below 1.0 because a dozen questions is a small sample of a person.
     */
    private static final double GRADED_CONFIDENCE = 0.80;

    private final AuthenticatedStudentService authenticatedStudentService;
    private final AssessmentPaperLoader assessmentPaperLoader;
    private final AssessmentPaperScorer assessmentPaperScorer;
    private final AssessmentRubricGrader assessmentRubricGrader;
    private final SkillEvidenceService skillEvidenceService;
    private final SkillProficiencyPromoter skillProficiencyPromoter;
    private final RoadmapRefreshTrigger roadmapRefreshTrigger;
    private final StudentAssessmentRepository studentAssessmentRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final SkillRepository skillRepository;

    @Value("${spring.ai.openai.chat.options.model:unknown}")
    private String modelName;

    // ── Paper ───────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Optional<GradedAssessmentPaperResponse> getPaper() {
        Student student = authenticatedStudentService.getRequiredStudent();
        CareerRole career = student.getCareerRole();
        if (career == null || career.getCareerName() == null) return Optional.empty();

        return assessmentPaperLoader.paperFor(career.getCareerName())
                .map(paper -> GradedAssessmentPaperResponse.builder()
                        .careerId(career.getCareerId())
                        .careerName(career.getCareerName())
                        .scope(paper.scope())
                        .version(paper.version())
                        .items(paper.items().stream().map(this::toItemResponse).toList())
                        .build());
    }

    // ── Submit ──────────────────────────────────────────────────────────────

    @Override
    public GradedAssessmentResultResponse submit(SubmitGradedAssessmentRequest request) {
        Student student = authenticatedStudentService.getRequiredStudent();
        CareerRole career = student.getCareerRole();
        if (career == null) {
            throw new BadRequestException("Pick a target career before taking the assessment.");
        }
        AssessmentPaper paper = assessmentPaperLoader.paperFor(career.getCareerName())
                .orElseThrow(() -> new BadRequestException(
                        "There is no graded assessment for " + career.getCareerName() + " yet."));

        UUID userId = student.getUserId();
        List<SubmittedAnswer> answers = validate(request, paper);

        // Auto-graded half first, in code. Nothing here can fail on a network.
        List<ItemOutcome> objective = assessmentPaperScorer.gradeObjective(paper, answers);

        StudentAssessment assessment = studentAssessmentRepository.save(StudentAssessment.builder()
                .userId(userId)
                .careerId(career.getCareerId())
                .questions(questionsAsJson(paper))
                .answers(answersAsJson(answers))
                .status(COMPLETED_STATUS)
                .modelUsed(modelName)
                .build());

        // ── The slow part, outside any transaction of ours ──────────────────
        Map<String, String> textByItemId = new HashMap<>();
        for (SubmittedAnswer answer : answers) {
            if (answer.text() != null && !answer.text().isBlank()) {
                textByItemId.put(answer.itemId(), answer.text());
            }
        }
        List<AssessmentItem> rubricItems = paper.items().stream()
                .filter(item -> !item.kind().isAutoGraded())
                .toList();
        List<RubricGrade> grades = assessmentRubricGrader.grade(rubricItems, textByItemId);

        List<ItemOutcome> rubricOutcomes = toRubricOutcomes(paper, grades);
        PaperVerdict verdict = assessmentPaperScorer.verdict(paper, objective, rubricOutcomes);

        upsertStudentSkills(userId, verdict.proficiencyBySkillId());
        List<UUID> evidenceIds = skillEvidenceService.recordAssessmentEvidence(
                userId, toSkillMatches(verdict.proficiencyBySkillId()), assessment.getAssessmentId());
        try {
            skillProficiencyPromoter.promoteFromEvidence(userId, evidenceIds);
        } catch (Exception e) {
            log.warn("GradedAssessmentServiceImpl: could not promote from {} evidence row(s) for "
                    + "user {}: {}", evidenceIds.size(), userId, e.getMessage());
        }

        assessment.setAiLevel(verdict.level());
        assessment.setAiRawLevel(verdict.objectiveLevel());
        assessment.setAiRationale(verdict.rationale());
        assessment.setAiConfidence(round(GRADED_CONFIDENCE));
        assessment.setRatioAll(round(verdict.objectiveScore()));
        assessment.setRatioVerified(verdict.rubricScore() == null ? null : round(verdict.rubricScore()));
        assessment.setRequiredCount(paper.items().size());
        assessment.setComputedAt(LocalDateTime.now());
        studentAssessmentRepository.save(assessment);

        // The whole point of grading someone is that their roadmap changes. This
        // path graded, evidenced and promoted, then reported `appliedNodeCount(0)`
        // and stopped — a student sat fourteen questions and returned to a canvas
        // with not one tick on it. The same trigger the GitHub import and the
        // transcript already use closes the loop, and every gate it runs
        // (completion policy, proficiency bar, importance floor) still applies:
        // this marks what the evidence earns, not everything the paper touched.
        List<UUID> markedNodeIds = roadmapRefreshTrigger.refreshAndCollect("graded-assessment");

        log.info("GradedAssessmentServiceImpl: user {} sat the {} paper — level {} (objective {}), "
                        + "tier reach {}, {} skill(s) evidenced.",
                userId, paper.scope(), verdict.level(), verdict.objectiveLevel(), verdict.tierReach(),
                verdict.proficiencyBySkillId().size());

        return GradedAssessmentResultResponse.builder()
                .assessmentId(assessment.getAssessmentId())
                .level(verdict.level().name())
                .objectiveLevel(verdict.objectiveLevel().name())
                .objectiveScore(round(verdict.objectiveScore()))
                .rubricScore(verdict.rubricScore() == null ? null : round(verdict.rubricScore()))
                .tierReach(verdict.tierReach())
                .rationale(verdict.rationale())
                .evidencedSkillCount(verdict.proficiencyBySkillId().size())
                .appliedNodeCount(markedNodeIds.size())
                .markedNodeIds(markedNodeIds)
                .items(toItemResults(paper, objective, grades))
                .build();
    }

    // ── Internals ───────────────────────────────────────────────────────────

    /**
     * Answers to questions we did not ask are rejected, not ignored.
     *
     * <p>An unknown item id means the client is out of step with the server — a
     * stale tab, or a paper edited between serving and submitting — and silently
     * dropping it would grade a paper nobody actually sat. Missing answers are fine:
     * an unanswered question scores zero, which is what leaving it blank means.
     */
    private List<SubmittedAnswer> validate(SubmitGradedAssessmentRequest request, AssessmentPaper paper) {
        List<SubmittedAnswer> answers = new ArrayList<>();
        for (AssessmentItemAnswerRequest submitted : request.getAnswers()) {
            AssessmentItem item = assessmentPaperLoader.itemOf(paper, submitted.getItemId())
                    .orElseThrow(() -> new BadRequestException(
                            "Unknown question '" + submitted.getItemId() + "'. Reload the assessment "
                                    + "and try again."));
            answers.add(new SubmittedAnswer(item.id(),
                    submitted.getChoiceKeys() == null ? List.of() : submitted.getChoiceKeys(),
                    submitted.getText()));
        }
        return answers;
    }

    /**
     * Turn the model's point awards into shares of each item's weight.
     *
     * <p>Clamped into 0..1 rather than trusted: a model that returns 12 out of 10
     * must not be able to lift a score above full marks, and one that returns a
     * negative number must not subtract from the rest of the paper.
     */
    private List<ItemOutcome> toRubricOutcomes(AssessmentPaper paper, List<RubricGrade> grades) {
        Map<String, RubricGrade> byItemId = new HashMap<>();
        for (RubricGrade grade : grades) {
            if (grade != null && grade.itemId() != null) byItemId.put(grade.itemId(), grade);
        }
        List<ItemOutcome> outcomes = new ArrayList<>();
        for (AssessmentItem item : paper.items()) {
            if (item.kind().isAutoGraded()) continue;
            RubricGrade grade = byItemId.get(item.id());
            if (grade == null) continue;
            int possible = AssessmentRubricGrader.possiblePoints(item);
            double share = possible == 0 ? 0 : (double) grade.awarded() / possible;
            outcomes.add(new ItemOutcome(item.id(), item.tier(), item.weight(),
                    Math.max(0, Math.min(1, share)), false));
        }
        return outcomes;
    }

    /**
     * Write what the paper evidenced onto the student's skill rows.
     *
     * <p><b>Never lowers anything.</b> A GitHub import that proved APPLIED must not be
     * undone by one missed question: an assessment is a sample, and a sample cannot
     * disprove a shipped repository. Rows already carrying a {@code verifiedBy} are
     * skipped entirely, the same rule the self-report path applies.
     */
    private void upsertStudentSkills(UUID userId, Map<UUID, ProficiencyLevel> proficiency) {
        if (proficiency.isEmpty()) return;

        Map<UUID, StudentSkill> existing = new HashMap<>();
        for (StudentSkill row : studentSkillRepository.findByStudent_UserId(userId)) {
            if (row.getSkill() != null && row.getSkill().getSkillId() != null) {
                existing.put(row.getSkill().getSkillId(), row);
            }
        }

        Map<UUID, Skill> skills = new HashMap<>();
        skillRepository.findAllById(proficiency.keySet())
                .forEach(skill -> skills.put(skill.getSkillId(), skill));

        List<StudentSkill> toSave = new ArrayList<>();
        for (Map.Entry<UUID, ProficiencyLevel> entry : proficiency.entrySet()) {
            short earned = (short) entry.getValue().getScore();
            StudentSkill row = existing.get(entry.getKey());
            if (row == null) {
                Skill skill = skills.get(entry.getKey());
                if (skill == null) continue;
                row = StudentSkill.builder()
                        .student(Student.builder().userId(userId).build())
                        .skill(skill)
                        .build();
            } else if (row.getVerifiedBy() != null && !row.getVerifiedBy().isBlank()) {
                continue;
            } else if (row.getProficiency() != null && row.getProficiency() >= earned) {
                continue;
            }
            row.setProficiency(earned);
            row.setSelfDeclared(false);
            row.setVerifiedBy(null);
            toSave.add(row);
        }

        if (!toSave.isEmpty()) {
            studentSkillRepository.saveAll(toSave);
            log.debug("GradedAssessmentServiceImpl: wrote proficiency onto {} skill row(s) for user {}.",
                    toSave.size(), userId);
        }
    }

    private List<SkillMatch> toSkillMatches(Map<UUID, ProficiencyLevel> proficiency) {
        List<SkillMatch> matches = new ArrayList<>();
        for (UUID skillId : proficiency.keySet()) {
            Skill skill = skillRepository.findBySkillId(skillId);
            if (skill != null && skill.getSkillName() != null) {
                matches.add(new SkillMatch(skill.getSkillName(), GRADED_CONFIDENCE));
            }
        }
        return matches;
    }

    private List<GradedItemResultResponse> toItemResults(AssessmentPaper paper,
                                                         List<ItemOutcome> objective,
                                                         List<RubricGrade> grades) {
        Map<String, ItemOutcome> objectiveByItemId = new HashMap<>();
        for (ItemOutcome outcome : objective) objectiveByItemId.put(outcome.itemId(), outcome);
        Map<String, RubricGrade> gradeByItemId = new HashMap<>();
        for (RubricGrade grade : grades) {
            if (grade != null && grade.itemId() != null) gradeByItemId.put(grade.itemId(), grade);
        }

        List<GradedItemResultResponse> results = new ArrayList<>();
        for (AssessmentItem item : paper.items()) {
            if (item.kind().isAutoGraded()) {
                ItemOutcome outcome = objectiveByItemId.get(item.id());
                boolean correct = outcome != null && outcome.earnedShare() >= 1.0;
                results.add(GradedItemResultResponse.builder()
                        .id(item.id()).topic(item.topic()).tier(item.tier())
                        .correct(correct)
                        .awarded(correct ? item.weight() : 0)
                        .possible(item.weight())
                        .correctKeys(item.answer())
                        .explanation(item.explanation())
                        .build());
            } else {
                RubricGrade grade = gradeByItemId.get(item.id());
                results.add(GradedItemResultResponse.builder()
                        .id(item.id()).topic(item.topic()).tier(item.tier())
                        .correct(null)
                        .awarded(grade == null ? 0 : grade.awarded())
                        .possible(AssessmentRubricGrader.possiblePoints(item))
                        .explanation(item.explanation())
                        .feedback(grade == null
                                ? "This answer could not be graded automatically this time."
                                : grade.feedback())
                        .build());
            }
        }
        return results;
    }

    private AssessmentItemResponse toItemResponse(AssessmentItem item) {
        return AssessmentItemResponse.builder()
                .id(item.id())
                .kind(item.kind().name())
                .tier(item.tier())
                .topic(item.topic())
                .prompt(item.prompt())
                .choices(item.choices() == null ? null : item.choices().stream()
                        .map(this::toChoiceResponse).toList())
                .language(item.language())
                .starterCode(item.starterCode())
                .points(item.kind().isAutoGraded()
                        ? item.weight()
                        : AssessmentRubricGrader.possiblePoints(item))
                .build();
    }

    private AssessmentChoiceResponse toChoiceResponse(AssessmentChoice choice) {
        return AssessmentChoiceResponse.builder().key(choice.key()).text(choice.text()).build();
    }

    /** The paper as served, frozen into the row so a later bank edit cannot rewrite history. */
    private List<Map<String, Object>> questionsAsJson(AssessmentPaper paper) {
        List<Map<String, Object>> json = new ArrayList<>();
        for (AssessmentItem item : paper.items()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("itemId", item.id());
            row.put("kind", item.kind().name());
            row.put("tier", item.tier());
            row.put("topic", item.topic());
            row.put("paperScope", paper.scope());
            row.put("paperVersion", paper.version());
            json.add(row);
        }
        return json;
    }

    private List<Map<String, Object>> answersAsJson(List<SubmittedAnswer> answers) {
        List<Map<String, Object>> json = new ArrayList<>();
        for (SubmittedAnswer answer : answers) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("itemId", answer.itemId());
            row.put("choiceKeys", answer.choiceKeys());
            row.put("text", answer.text());
            json.add(row);
        }
        return json;
    }

    private BigDecimal round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
