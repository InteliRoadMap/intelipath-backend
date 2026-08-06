package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.components.AssessmentPaperScorer.ItemOutcome;
import com.inteliroadmap.backend.components.AssessmentPaperScorer.PaperVerdict;
import com.inteliroadmap.backend.components.AssessmentPaperScorer.SubmittedAnswer;
import com.inteliroadmap.backend.domain.enums.AssessmentItemKind;
import com.inteliroadmap.backend.domain.enums.ProficiencyLevel;
import com.inteliroadmap.backend.domain.enums.SeniorityLevel;
import com.inteliroadmap.backend.domain.model.AssessmentChoice;
import com.inteliroadmap.backend.domain.model.AssessmentItem;
import com.inteliroadmap.backend.domain.model.AssessmentPaper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssessmentPaperScorerTest {

    private AssessmentPaperScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new AssessmentPaperScorer();
    }

    // ---------- the answer key ----------

    @Test
    void aCorrectChoiceScoresAndAWrongOneDoesNot() {
        AssessmentPaper paper = paperOf(mcq("q1", 1, "b"), mcq("q2", 1, "a"));

        List<ItemOutcome> outcomes = scorer.gradeObjective(paper, List.of(
                answer("q1", "b"), answer("q2", "c")));

        assertEquals(1.0, shareOf(outcomes, "q1"));
        assertEquals(0.0, shareOf(outcomes, "q2"));
    }

    /** A client that reorders the keys of a multi-answer item must not be marked wrong. */
    @Test
    void multipleKeysAreComparedAsASetNotAList() {
        AssessmentPaper paper = paperOf(multi("q1", 2, List.of("a", "c")));

        assertEquals(1.0, shareOf(scorer.gradeObjective(paper, List.of(answer("q1", "c", "a"))), "q1"));
    }

    /** Naming two of three right answers is not two-thirds of the judgement. */
    @Test
    void multiChoiceGivesNoPartialCredit() {
        AssessmentPaper paper = paperOf(multi("q1", 2, List.of("a", "b", "c")));

        assertEquals(0.0, shareOf(scorer.gradeObjective(paper, List.of(answer("q1", "a", "b"))), "q1"));
    }

    @Test
    void anUnansweredItemScoresZeroRatherThanBeingSkipped() {
        AssessmentPaper paper = paperOf(mcq("q1", 1, "b"), mcq("q2", 1, "a"));

        List<ItemOutcome> outcomes = scorer.gradeObjective(paper, List.of(answer("q1", "b")));

        assertEquals(2, outcomes.size(), "the unanswered item must still be on the paper");
        assertEquals(0.0, shareOf(outcomes, "q2"));
    }

    // ---------- the level ----------

    /**
     * The bug this guards against: four options means a blank paper scores 0.25 by
     * chance, which lands at FRESHER on any naive banding — a level awarded for
     * opening the form.
     */
    @Test
    void answeringNothingIsBeginnerNotFresher() {
        AssessmentPaper paper = paperOf(mcq("q1", 1, "a"), mcq("q2", 2, "a"), mcq("q3", 3, "a"));

        PaperVerdict verdict = scorer.verdict(paper, scorer.gradeObjective(paper, List.of()), List.of());

        assertEquals(SeniorityLevel.BEGINNER, verdict.level());
        assertEquals(0, verdict.tierReach());
    }

    @Test
    void aPerfectPaperReachesTheTopBand() {
        AssessmentPaper paper = paperOf(mcq("q1", 1, "a"), mcq("q2", 2, "a"), mcq("q3", 3, "a"));

        PaperVerdict verdict = scorer.verdict(paper, scorer.gradeObjective(paper,
                List.of(answer("q1", "a"), answer("q2", "a"), answer("q3", "a"))), List.of());

        assertEquals(SeniorityLevel.EXPERT, verdict.level());
        assertEquals(3, verdict.tierReach());
    }

    /**
     * Tier weighting has to be doing something. Two students with the same number
     * of correct answers, one of whom answered the hard ones, must not tie.
     */
    @Test
    void gettingTheHardOnesRightScoresHigherThanGettingTheEasyOnes() {
        AssessmentPaper paper = paperOf(
                mcq("e1", 1, "a"), mcq("e2", 1, "a"), mcq("h1", 3, "a"), mcq("h2", 3, "a"));

        double easyOnly = scorer.verdict(paper,
                scorer.gradeObjective(paper, List.of(answer("e1", "a"), answer("e2", "a"))),
                List.of()).objectiveScore();
        double hardOnly = scorer.verdict(paper,
                scorer.gradeObjective(paper, List.of(answer("h1", "a"), answer("h2", "a"))),
                List.of()).objectiveScore();

        assertTrue(hardOnly > easyOnly,
                "if these are equal the tier column is decoration: " + hardOnly + " vs " + easyOnly);
    }

    /**
     * The clamp. A fluent essay must not be able to argue a paper up several bands
     * past what the reproducible half supports — this is also what limits the damage
     * from a prompt injection inside a student's answer.
     */
    @Test
    void aPerfectRubricScoreCannotLiftAWeakPaperMoreThanOneBand() {
        AssessmentPaper paper = paperOf(
                mcq("q1", 1, "a"), mcq("q2", 1, "a"), mcq("q3", 1, "a"), mcq("q4", 1, "a"),
                code("c1", 3));

        List<ItemOutcome> objective = scorer.gradeObjective(paper, List.of(answer("q1", "a")));
        List<ItemOutcome> rubric = List.of(new ItemOutcome("c1", 3, 3, 1.0, false));

        PaperVerdict verdict = scorer.verdict(paper, objective, rubric);

        assertEquals(SeniorityLevel.BEGINNER, verdict.objectiveLevel());
        assertTrue(verdict.level().ordinal() <= SeniorityLevel.FRESHER.ordinal(),
                "one band above BEGINNER at most, got " + verdict.level());
        assertTrue(verdict.rationale().contains("held one band"),
                "the student must be told why the two halves did not add up");
    }

    /** An unreachable model costs the rubric half, not the sitting. */
    @Test
    void aMissingRubricScoreLeavesTheLevelOnTheObjectiveHalf() {
        AssessmentPaper paper = paperOf(mcq("q1", 1, "a"), mcq("q2", 1, "a"), code("c1", 2));

        PaperVerdict verdict = scorer.verdict(paper,
                scorer.gradeObjective(paper, List.of(answer("q1", "a"), answer("q2", "a"))),
                List.of());

        assertNull(verdict.rubricScore());
        assertEquals(verdict.objectiveLevel(), verdict.level());
        assertTrue(verdict.rationale().contains("could not be graded automatically"));
    }

    // ---------- evidence ----------

    @Test
    void aSkillAnsweredWellIsEvidencedAndOneAnsweredBadlyIsNot() {
        UUID caching = UUID.randomUUID();
        UUID oop = UUID.randomUUID();
        AssessmentPaper paper = paperOf(
                mcq("q1", 1, "a").withSkillIds(List.of(caching)),
                mcq("q2", 1, "a").withSkillIds(List.of(caching)),
                mcq("q3", 1, "a").withSkillIds(List.of(oop)));

        PaperVerdict verdict = scorer.verdict(paper,
                scorer.gradeObjective(paper, List.of(answer("q1", "a"), answer("q2", "a"),
                        answer("q3", "d"))),
                List.of());

        assertEquals(ProficiencyLevel.APPLIED, verdict.proficiencyBySkillId().get(caching));
        assertNull(verdict.proficiencyBySkillId().get(oop),
                "a wrong answer is not evidence that the student is even AWARE of the skill");
    }

    /** A skill the paper never asked about has no entry — no opinion is not a bad opinion. */
    @Test
    void anUnaskedSkillGetsNoVerdict() {
        UUID asked = UUID.randomUUID();
        AssessmentPaper paper = paperOf(mcq("q1", 1, "a").withSkillIds(List.of(asked)));

        PaperVerdict verdict = scorer.verdict(paper,
                scorer.gradeObjective(paper, List.of(answer("q1", "a"))), List.of());

        assertEquals(1, verdict.proficiencyBySkillId().size());
    }

    // ---------- fixtures ----------

    private AssessmentPaper paperOf(AssessmentItem... items) {
        return new AssessmentPaper("TEST", List.of("Test"), 1, List.of(items));
    }

    private AssessmentItem mcq(String id, int tier, String correctKey) {
        return multi(id, tier, List.of(correctKey));
    }

    private AssessmentItem multi(String id, int tier, List<String> correctKeys) {
        List<AssessmentChoice> choices = new ArrayList<>();
        for (String key : List.of("a", "b", "c", "d")) choices.add(new AssessmentChoice(key, key));
        AssessmentItemKind kind = correctKeys.size() > 1
                ? AssessmentItemKind.MULTI_CHOICE : AssessmentItemKind.SINGLE_CHOICE;
        return new AssessmentItem(id, kind, tier, "topic", List.of(), "prompt",
                choices, correctKeys, null, null, List.of(), "because", List.of());
    }

    private AssessmentItem code(String id, int tier) {
        return new AssessmentItem(id, AssessmentItemKind.CODE, tier, "topic", List.of(), "prompt",
                null, List.of(), "java", "starter", List.of(), "because", List.of());
    }

    private SubmittedAnswer answer(String itemId, String... keys) {
        return new SubmittedAnswer(itemId, List.of(keys), null);
    }

    private double shareOf(List<ItemOutcome> outcomes, String itemId) {
        return outcomes.stream().filter(o -> o.itemId().equals(itemId))
                .findFirst().orElseThrow().earnedShare();
    }
}
