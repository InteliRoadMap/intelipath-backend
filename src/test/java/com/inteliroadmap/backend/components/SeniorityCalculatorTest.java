package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.domain.entity.CareerRequiredSkill;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.StudentSkill;
import com.inteliroadmap.backend.domain.enums.ImportanceLevel;
import com.inteliroadmap.backend.domain.enums.SeniorityLevel;
import com.inteliroadmap.backend.repositories.CareerRequiredSkillRepository;
import com.inteliroadmap.backend.repositories.StudentSkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Pins the six-rung ladder down.
 *
 * <p>The ladder used to be asserted only by the DB CHECK on
 * {@code student_assessments.ai_level}, which listed four of the six values.
 * That made every BEGINNER verdict a constraint violation on the second save in
 * {@code submitAssessment} — a 500 raised <em>after</em> the model call had been
 * paid for. Nothing in the test suite would have caught it, because nothing
 * asserted what {@link SeniorityCalculator#compute} is allowed to return.
 */
@ExtendWith(MockitoExtension.class)
class SeniorityCalculatorTest {

    @Mock
    private CareerRequiredSkillRepository careerRequiredSkillRepository;
    @Mock
    private StudentSkillRepository studentSkillRepository;

    private SeniorityCalculator calculator;

    private final UUID careerId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        calculator = new SeniorityCalculator(careerRequiredSkillRepository, studentSkillRepository);
    }

    /**
     * Why the denominator is HIGH-only, stated as arithmetic.
     *
     * <p>Against HIGH+AVG, Backend's core set is 181 skills while one assessment
     * grades at most {@link AssessmentQuestionBuilder#MAX_QUESTIONS} = 15, so the
     * best possible result was 8.3% — under {@link SeniorityCalculator#FRESHER_AT}.
     * Every Backend student came out BEGINNER regardless of what they proved. A
     * level that cannot vary measures nothing.
     *
     * <p>Against HIGH alone the same career is 29 skills, so the same 15 answers
     * reach 52% and land on JUNIOR. This test holds both halves so the reasoning
     * survives the next person who wonders why the set is what it is.
     */
    @Test
    void theHighOnlyDenominatorLetsOneAssessmentMoveTheLadder() {
        int graded = AssessmentQuestionBuilder.MAX_QUESTIONS;

        stubCareerAndStudent(181, graded, 0);
        assertEquals(SeniorityLevel.BEGINNER, calculator.compute(userId, careerId).level(),
                "the old HIGH+AVG denominator: 15 of 181 is 8.3%, pinned under FRESHER");

        stubCareerAndStudent(29, graded, 0);
        assertEquals(SeniorityLevel.JUNIOR, calculator.compute(userId, careerId).level(),
                "HIGH only: the same 15 answers are 52% of Backend's real bar");
    }

    /** BEGINNER is still reachable and still has to be storable — just no longer forced. */
    @Test
    void aStudentWhoEvidencesAlmostNothingIsBeginner() {
        stubCareerAndStudent(29, 2, 0);

        SeniorityCalculator.SeniorityVerdict verdict = calculator.compute(userId, careerId);

        assertEquals(SeniorityLevel.BEGINNER, verdict.level());
        assertTrue(verdict.ratioAll().doubleValue() < SeniorityCalculator.FRESHER_AT);
    }

    /** The top rung is reachable; it was unreachable while the ladder stopped at SENIOR. */
    @Test
    void fullyVerifiedCoverageReachesExpert() {
        stubCareerAndStudent(100, 100, 100);

        assertEquals(SeniorityLevel.EXPERT, calculator.compute(userId, careerId).level());
    }

    /**
     * The rule that makes self-assessment safe: ticking PROFESSIONAL on everything
     * gives coverage 1.0 with nothing verified, and must still cap at JUNIOR.
     */
    @Test
    void selfDeclarationAloneCannotExceedJunior() {
        stubCareerAndStudent(100, 100, 0);

        SeniorityCalculator.SeniorityVerdict verdict = calculator.compute(userId, careerId);

        assertEquals(SeniorityLevel.EXPERT, verdict.rawLevel(), "coverage really is 100%");
        assertEquals(SeniorityLevel.JUNIOR, verdict.level(), "but none of it is verified");
    }

    /** One verified skill short of the floor still caps; one over it does not. */
    @Test
    void theVerifiedFloorAppliesAtExactlyThirtyPercent() {
        stubCareerAndStudent(100, 100, 29);
        assertEquals(SeniorityLevel.JUNIOR, calculator.compute(userId, careerId).level());

        stubCareerAndStudent(100, 100, 30);
        assertEquals(SeniorityLevel.EXPERT, calculator.compute(userId, careerId).level());
    }

    /**
     * A row with no proficiency predates the assessment — we have never asked how
     * well the student knows it, so it cannot count towards a level claim.
     */
    @Test
    void aSkillRowWithNoProficiencyDoesNotCount() {
        List<CareerRequiredSkill> career = new ArrayList<>();
        List<StudentSkill> held = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Skill skill = skill("Core " + i);
            career.add(required(skill));
            held.add(studentSkill(skill, null, null));
        }
        stub(career, held);

        SeniorityCalculator.SeniorityVerdict verdict = calculator.compute(userId, careerId);

        assertEquals(SeniorityLevel.BEGINNER, verdict.level());
        assertEquals(0, verdict.ratioAll().compareTo(java.math.BigDecimal.ZERO));
    }

    /** PRACTICED is below COUNTS_AS_HELD; only APPLIED and above are evidence of holding a skill. */
    @Test
    void practicedIsNotYetHeld() {
        List<CareerRequiredSkill> career = new ArrayList<>();
        List<StudentSkill> held = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Skill skill = skill("Core " + i);
            career.add(required(skill));
            held.add(studentSkill(skill, (short) 2, null));
        }
        stub(career, held);

        assertEquals(SeniorityLevel.BEGINNER, calculator.compute(userId, careerId).level());
    }

    /** No target data is not a judgement about the student, and must not divide by zero. */
    @Test
    void aCareerWithNoRequiredSkillsYieldsBeginnerNotAnError() {
        stub(List.of(), List.of());

        SeniorityCalculator.SeniorityVerdict verdict = calculator.compute(userId, careerId);

        assertEquals(SeniorityLevel.BEGINNER, verdict.level());
        assertEquals(0, verdict.requiredCount());
    }

    /**
     * Every value the calculator can return has to be storable. This is the test
     * that would have caught the four-value CHECK constraint.
     */
    @Test
    void everyLevelTheCalculatorReturnsIsOnTheLadder() {
        List<SeniorityLevel> ladder = List.of(SeniorityLevel.LADDER);
        int[][] cases = {{100, 0, 0}, {100, 15, 0}, {100, 50, 50}, {100, 75, 75},
                         {100, 90, 90}, {100, 100, 100}, {181, 15, 0}};

        for (int[] c : cases) {
            stubCareerAndStudent(c[0], c[1], c[2]);
            SeniorityCalculator.SeniorityVerdict verdict = calculator.compute(userId, careerId);
            assertTrue(ladder.contains(verdict.level()),
                    verdict.level() + " is not on SeniorityLevel.LADDER");
            assertTrue(ladder.contains(verdict.rawLevel()),
                    verdict.rawLevel() + " is not on SeniorityLevel.LADDER");
        }
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    /**
     * @param coreSkills how many HIGH/AVG rows the career has — the denominator
     * @param heldCount  how many of them the student holds at APPLIED or better
     * @param verified   how many of the held ones carry an objective verifier
     */
    private void stubCareerAndStudent(int coreSkills, int heldCount, int verified) {
        List<CareerRequiredSkill> career = new ArrayList<>();
        List<StudentSkill> held = new ArrayList<>();
        for (int i = 0; i < coreSkills; i++) {
            Skill skill = skill("Core " + i);
            career.add(required(skill));
            if (i < heldCount) {
                held.add(studentSkill(skill, (short) 3, i < verified ? "GITHUB" : null));
            }
        }
        stub(career, held);
    }

    private void stub(List<CareerRequiredSkill> career, List<StudentSkill> held) {
        when(careerRequiredSkillRepository.findByCareerRole_CareerIdAndImportanceLevelIn(
                eq(careerId), any())).thenReturn(career);
        if (!career.isEmpty()) {
            when(studentSkillRepository.findByStudent_UserId(userId)).thenReturn(held);
        }
    }

    private Skill skill(String name) {
        Skill skill = new Skill();
        skill.setSkillId(UUID.randomUUID());
        skill.setSkillName(name);
        return skill;
    }

    private StudentSkill studentSkill(Skill skill, Short proficiency, String verifiedBy) {
        return StudentSkill.builder()
                .student(Student.builder().userId(userId).build())
                .skill(skill)
                .proficiency(proficiency)
                .verifiedBy(verifiedBy)
                .build();
    }

    private CareerRequiredSkill required(Skill skill) {
        return CareerRequiredSkill.builder()
                .skill(skill)
                .importanceLevel(ImportanceLevel.HIGH)
                .build();
    }
}
