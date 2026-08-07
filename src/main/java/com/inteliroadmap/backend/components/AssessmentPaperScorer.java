package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.domain.enums.AssessmentItemKind;
import com.inteliroadmap.backend.domain.enums.ProficiencyLevel;
import com.inteliroadmap.backend.domain.enums.SeniorityLevel;
import com.inteliroadmap.backend.domain.model.AssessmentItem;
import com.inteliroadmap.backend.domain.model.AssessmentPaper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Turns a sat paper into a level, and into per-skill evidence.
 *
 * <h2>Two scores, deliberately kept apart</h2>
 *
 * <p>The <b>objective</b> score comes from the multiple-choice items and an answer
 * key. It is computed in this class, it is reproducible, and it does not depend on
 * a model being reachable. The <b>rubric</b> score comes from the LLM reading the
 * written and code answers against criteria written in the bank.
 *
 * <p>They are combined rather than averaged blindly, because they can be gamed in
 * opposite directions. Multiple choice can be guessed: four options means a
 * candidate who knows nothing scores about a quarter. Prose can be bluffed: a
 * fluent answer that names the right words without understanding them reads well.
 * So the rubric score is <b>clamped to within one band of the objective score</b>
 * — see {@link #RUBRIC_LEAD_CAP}. A paper whose multiple choice says FRESHER cannot
 * be argued up to SENIOR by an essay, and equally a strong essay is not thrown away
 * by two unlucky guesses.
 *
 * <h2>Tier weighting is the level signal</h2>
 *
 * <p>Items are worth their tier, so eight easy answers and no hard ones score
 * differently from four of each even when the raw count matches. This is the same
 * 1/2/3 banding {@code RoadmapTierResolver} uses to decide what a student is shown,
 * so "the level said MID" and "the roadmap unlocked tier 3" cannot disagree about
 * what tier 3 means.
 *
 * <h2>Guessing is priced in</h2>
 *
 * <p>A four-option item is worth a quarter of its weight to someone answering at
 * random, so a blank paper scores ~0.25 and would otherwise land at FRESHER. The
 * bands below therefore start from {@link #GUESS_FLOOR} rather than from zero: the
 * share that counts is how much of the <em>gap above guessing</em> was earned.
 */
@Component
@Slf4j
public class AssessmentPaperScorer {

    /** Expected share of a multiple-choice paper from answering at random. */
    public static final double GUESS_FLOOR = 0.25;

    /**
     * How far the rubric score may pull the final level away from the objective one.
     *
     * <p>One band. The two scores measure different things and are allowed to
     * disagree — that disagreement is information — but a paper where they disagree
     * by three bands is one where something is being gamed, and the reproducible
     * half is the one to trust.
     */
    public static final int RUBRIC_LEAD_CAP = 1;

    /**
     * Weight of the objective score in the blend, before clamping.
     *
     * <p>Above half, because it is the part that cannot be talked around. Not much
     * above, because ten multiple-choice items is a thin instrument on its own and
     * the code answers are where a real engineer separates from a memoriser.
     */
    public static final double OBJECTIVE_WEIGHT = 0.6;

    /** Share of the earnable range at or above which each band starts. */
    public static final double EXPERT_AT = 0.92;
    public static final double SENIOR_AT = 0.78;
    public static final double MID_AT = 0.60;
    public static final double JUNIOR_AT = 0.40;
    public static final double FRESHER_AT = 0.18;

    /**
     * Correct share at or above which one skill is credited as APPLIED.
     *
     * <p>{@code SeniorityCalculator.COUNTS_AS_HELD} is APPLIED, so this is the bar
     * for a paper to count as evidence of holding a skill at all. Two thirds rather
     * than everything: an assessment is a sample, and demanding a clean sweep of
     * every item touching a skill would credit almost nobody.
     */
    public static final double APPLIED_AT = 0.67;

    /** Correct share below which the paper says the student is merely aware of it. */
    public static final double PRACTICED_AT = 0.34;

    /**
     * What one student did with one paper.
     *
     * @param objectiveScore  0..1 over the auto-graded items alone
     * @param rubricScore     0..1 over the rubric-graded items, or null when the
     *                        model could not be reached — the level then rests on the
     *                        objective half rather than being invented
     * @param tierReach       the highest tier at which the student got a majority
     *                        right; what a roadmap should actually unlock
     * @param proficiencyBySkillId what the paper evidences, per catalog skill
     */
    public record PaperVerdict(SeniorityLevel level,
                               SeniorityLevel objectiveLevel,
                               double objectiveScore,
                               Double rubricScore,
                               int tierReach,
                               Map<UUID, ProficiencyLevel> proficiencyBySkillId,
                               String rationale) {}

    /** One answer as submitted, before it is compared with anything. */
    public record SubmittedAnswer(String itemId, List<String> choiceKeys, String text) {}

    /** The result of grading one item, whichever half graded it. */
    public record ItemOutcome(String itemId, int tier, int weight, double earnedShare, boolean autoGraded) {}

    /**
     * Grade the auto-gradable half.
     *
     * <p>Split from {@link #verdict} so it can run before the model is called and
     * still be on record if the call times out — the same reason the assessment row
     * is persisted before the LLM is invoked.
     */
    public List<ItemOutcome> gradeObjective(AssessmentPaper paper, List<SubmittedAnswer> answers) {
        Map<String, SubmittedAnswer> byItemId = new HashMap<>();
        for (SubmittedAnswer answer : answers) {
            if (answer != null && answer.itemId() != null) byItemId.put(answer.itemId(), answer);
        }

        List<ItemOutcome> outcomes = new ArrayList<>();
        for (AssessmentItem item : paper.items()) {
            if (!item.kind().isAutoGraded()) continue;
            SubmittedAnswer answer = byItemId.get(item.id());
            outcomes.add(new ItemOutcome(item.id(), item.tier(), item.weight(),
                    isCorrect(item, answer) ? 1.0 : 0.0, true));
        }
        return outcomes;
    }

    /**
     * Combine both halves into a level.
     *
     * @param rubricOutcomes what the model awarded per rubric item, as a 0..1 share
     *                       of that item's rubric points. Empty when the model was
     *                       unreachable.
     */
    public PaperVerdict verdict(AssessmentPaper paper,
                                List<ItemOutcome> objectiveOutcomes,
                                List<ItemOutcome> rubricOutcomes) {
        double objectiveScore = weightedShare(objectiveOutcomes);
        Double rubricScore = rubricOutcomes.isEmpty() ? null : weightedShare(rubricOutcomes);

        SeniorityLevel objectiveLevel = bandOf(aboveGuessing(objectiveScore));

        double blended = rubricScore == null
                ? aboveGuessing(objectiveScore)
                : OBJECTIVE_WEIGHT * aboveGuessing(objectiveScore) + (1 - OBJECTIVE_WEIGHT) * rubricScore;
        SeniorityLevel blendedLevel = bandOf(blended);

        // The clamp. A blend that has run more than one band away from the
        // reproducible half is pulled back to one band away from it.
        SeniorityLevel level = clampWithin(blendedLevel, objectiveLevel, RUBRIC_LEAD_CAP);

        List<ItemOutcome> all = new ArrayList<>(objectiveOutcomes);
        all.addAll(rubricOutcomes);
        int tierReach = tierReach(all);

        String rationale = rationale(objectiveScore, rubricScore, tierReach, blendedLevel, level);
        log.info("AssessmentPaperScorer: {} — objective {} ({}), rubric {}, tier reach {}, level {}.",
                paper.scope(), String.format(Locale.ROOT, "%.2f", objectiveScore), objectiveLevel,
                rubricScore == null ? "n/a" : String.format(Locale.ROOT, "%.2f", rubricScore),
                tierReach, level);

        return new PaperVerdict(level, objectiveLevel, objectiveScore, rubricScore, tierReach,
                proficiencyBySkill(paper, all), rationale);
    }

    /**
     * What a correct answer looks like.
     *
     * <p>Set comparison, not list: {@code ["a","c"]} and {@code ["c","a"]} are the
     * same answer, and a client that reorders the keys must not be marked wrong.
     * No partial credit on MULTI_CHOICE — "which three of these are true" is a
     * single judgement, and awarding two-thirds for naming two of them rewards
     * shotgunning.
     */
    private boolean isCorrect(AssessmentItem item, SubmittedAnswer answer) {
        if (answer == null || answer.choiceKeys() == null || answer.choiceKeys().isEmpty()) return false;
        if (item.answer() == null || item.answer().isEmpty()) return false;
        Set<String> expected = new HashSet<>(item.answer().stream().map(this::normalise).toList());
        Set<String> given = new HashSet<>(answer.choiceKeys().stream().map(this::normalise).toList());
        return expected.equals(given);
    }

    private String normalise(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    private double weightedShare(List<ItemOutcome> outcomes) {
        int totalWeight = outcomes.stream().mapToInt(ItemOutcome::weight).sum();
        if (totalWeight == 0) return 0;
        double earned = outcomes.stream()
                .mapToDouble(outcome -> outcome.weight() * outcome.earnedShare())
                .sum();
        return earned / totalWeight;
    }

    /**
     * The share of the range above random guessing that was actually earned.
     *
     * <p>Without this a blank multiple-choice paper scores 0.25 and lands at
     * FRESHER, which is a level awarded for opening the form.
     */
    private double aboveGuessing(double rawScore) {
        return Math.max(0, (rawScore - GUESS_FLOOR) / (1 - GUESS_FLOOR));
    }

    /**
     * The highest tier the student got a majority of right.
     *
     * <p>Separate from the level because it answers a different question: the level
     * says how this student compares with the role, the tier reach says what the
     * roadmap can safely stop hiding. A student can score MID overall while missing
     * every tier-3 item, and the roadmap should believe the tier-3 result.
     */
    private int tierReach(List<ItemOutcome> outcomes) {
        int reach = 0;
        for (int tier = 1; tier <= 3; tier++) {
            final int currentTier = tier;
            List<ItemOutcome> atTier = outcomes.stream()
                    .filter(outcome -> outcome.tier() == currentTier).toList();
            if (atTier.isEmpty()) continue;
            double share = atTier.stream().mapToDouble(ItemOutcome::earnedShare).average().orElse(0);
            if (share > 0.5) reach = tier;
        }
        return reach;
    }

    /**
     * What the paper says about each skill it touched.
     *
     * <p>Averaged across every item naming the skill, so one lucky guess on a
     * two-item skill cannot produce an APPLIED claim on its own. Skills the paper
     * did not probe are absent rather than zero — the assessment has no opinion
     * about them, which is not the same as a bad opinion.
     */
    private Map<UUID, ProficiencyLevel> proficiencyBySkill(AssessmentPaper paper, List<ItemOutcome> outcomes) {
        Map<String, ItemOutcome> byItemId = new HashMap<>();
        for (ItemOutcome outcome : outcomes) byItemId.put(outcome.itemId(), outcome);

        Map<UUID, double[]> earnedAndCount = new HashMap<>();
        for (AssessmentItem item : paper.items()) {
            ItemOutcome outcome = byItemId.get(item.id());
            if (outcome == null || item.skillIds() == null) continue;
            for (UUID skillId : item.skillIds()) {
                double[] acc = earnedAndCount.computeIfAbsent(skillId, k -> new double[2]);
                acc[0] += outcome.earnedShare();
                acc[1] += 1;
            }
        }

        Map<UUID, ProficiencyLevel> proficiency = new HashMap<>();
        for (Map.Entry<UUID, double[]> entry : earnedAndCount.entrySet()) {
            double share = entry.getValue()[1] == 0 ? 0 : entry.getValue()[0] / entry.getValue()[1];
            if (share >= APPLIED_AT) {
                proficiency.put(entry.getKey(), ProficiencyLevel.APPLIED);
            } else if (share >= PRACTICED_AT) {
                proficiency.put(entry.getKey(), ProficiencyLevel.PRACTICED);
            }
            // Below PRACTICED_AT nothing is recorded. AWARE means "I have read about
            // it", and a wrong answer is not evidence that the student has even done
            // that - the same line upsertStudentSkills already draws.
        }
        return proficiency;
    }

    private SeniorityLevel bandOf(double share) {
        if (share >= EXPERT_AT) return SeniorityLevel.EXPERT;
        if (share >= SENIOR_AT) return SeniorityLevel.SENIOR;
        if (share >= MID_AT) return SeniorityLevel.MID;
        if (share >= JUNIOR_AT) return SeniorityLevel.JUNIOR;
        if (share >= FRESHER_AT) return SeniorityLevel.FRESHER;
        return SeniorityLevel.BEGINNER;
    }

    /** Pulls {@code candidate} back to at most {@code bands} away from {@code anchor}. */
    private SeniorityLevel clampWithin(SeniorityLevel candidate, SeniorityLevel anchor, int bands) {
        SeniorityLevel[] ladder = {SeniorityLevel.BEGINNER, SeniorityLevel.FRESHER, SeniorityLevel.JUNIOR,
                SeniorityLevel.MID, SeniorityLevel.SENIOR, SeniorityLevel.EXPERT};
        int candidateIndex = indexOf(ladder, candidate);
        int anchorIndex = indexOf(ladder, anchor);
        int clamped = Math.max(anchorIndex - bands, Math.min(anchorIndex + bands, candidateIndex));
        return ladder[clamped];
    }

    private int indexOf(SeniorityLevel[] ladder, SeniorityLevel level) {
        for (int i = 0; i < ladder.length; i++) {
            if (ladder[i] == level) return i;
        }
        return 0;
    }

    /**
     * One sentence the student can be shown, in their own numbers.
     *
     * <p>A level with no reason attached reads as a verdict. The clamp in
     * particular has to be stated: a student whose essay was strong and whose
     * multiple choice was not deserves to know that is why the two did not add up.
     */
    private String rationale(double objectiveScore, Double rubricScore, int tierReach,
                             SeniorityLevel blended, SeniorityLevel finalLevel) {
        StringBuilder sentence = new StringBuilder();
        sentence.append(String.format(Locale.ROOT,
                "Scored %.0f%% on the multiple-choice section", objectiveScore * 100));
        if (rubricScore != null) {
            sentence.append(String.format(Locale.ROOT,
                    " and %.0f%% against the rubric on the written and code answers",
                    rubricScore * 100));
        } else {
            sentence.append("; the written answers could not be graded automatically this time");
        }
        sentence.append(tierReach == 0
                ? ", with no tier answered correctly more often than not."
                : String.format(Locale.ROOT, ", answering tier %d questions correctly more often than not.",
                        tierReach));
        if (blended != finalLevel) {
            sentence.append(" The written answers pointed higher than the multiple choice supported,"
                    + " so the result was held one band from the objective score.");
        }
        return sentence.toString();
    }
}
