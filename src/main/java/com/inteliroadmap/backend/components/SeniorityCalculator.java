package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.domain.entity.CareerRequiredSkill;
import com.inteliroadmap.backend.domain.entity.StudentSkill;
import com.inteliroadmap.backend.domain.enums.ImportanceLevel;
import com.inteliroadmap.backend.domain.enums.SeniorityLevel;
import com.inteliroadmap.backend.repositories.CareerRequiredSkillRepository;
import com.inteliroadmap.backend.repositories.StudentSkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Derives a student's career level from their skill rows.
 *
 * <p>The level is a <em>function over the student's skills</em>, never a stored
 * field they can edit. Raising it means adding evidence, not changing a setting —
 * which is the only version of a level that a counselor or an employer could
 * take seriously.
 *
 * <p>The denominator is always the JUNIOR bar for the target career, whatever
 * level the student is at, so two students' readiness numbers are comparable.
 * Measuring each against their own level would make the counselor dashboard
 * compare figures computed against different baselines.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SeniorityCalculator {

    /** Proficiency at or above which a skill counts as "held" (APPLIED). */
    public static final int COUNTS_AS_HELD = 3;

    /**
     * The importance grades that make a skill part of the role's core.
     *
     * <p>{@code career_required_skills} is the whole catalog scoped by career, not
     * a requirement list, so the denominator has been narrowed twice. First from
     * every row to HIGH+AVG, because measuring against all 504 of Frontend's rows
     * pinned every student near zero. That was still too wide: HIGH+AVG leaves
     * Backend at 181 skills while one assessment grades at most
     * {@link AssessmentQuestionBuilder#MAX_QUESTIONS} = 15 of them, so 15/181 =
     * 8.3% lands under {@link #FRESHER_AT} and <em>every</em> Backend student came
     * out BEGINNER no matter how much they proved — a level that cannot vary is
     * not a measurement.
     *
     * <p>HIGH alone gives 16 (QA) to 29 (Backend, Frontend) skills per career, so
     * one assessment covers 52–94% of the bar and the ladder can actually move.
     * The remaining AVG rows are not discarded: they still appear on the roadmap
     * and still carry market demand, they just do not define what the role
     * requires.
     */
    public static final Set<ImportanceLevel> CORE_IMPORTANCE =
            Set.of(ImportanceLevel.HIGH);

    /**
     * Coverage bands, highest first. The scale runs below FRESHER and above
     * SENIOR because the assessment's job is to say where the student actually
     * is, and both ends are real answers.
     *
     * <p>The previous scale topped out at {@code MID_AT = 0.80}, which made
     * SENIOR unreachable no matter what a student proved, and had no rung under
     * FRESHER at all — so someone who could evidence nothing was labelled the
     * same as someone halfway to JUNIOR.
     */
    public static final double EXPERT_AT = 0.95;
    public static final double SENIOR_AT = 0.85;
    public static final double MID_AT = 0.70;
    public static final double JUNIOR_AT = 0.45;
    /** Below this, the student cannot evidence the role's foundations yet. */
    public static final double FRESHER_AT = 0.10;

    /**
     * Verified share below which the level cannot exceed JUNIOR.
     *
     * <p>This is the rule that makes the whole self-assessment safe: ticking
     * PROFESSIONAL on everything produces a coverage of 1.0 and a verified share
     * of 0.0, so it lands on JUNIOR, not MID. Self-declaration alone can never
     * buy a senior label — and that gives the student a concrete reason to
     * connect GitHub or upload a transcript, which is where real evidence comes
     * from.
     */
    public static final double VERIFIED_FLOOR = 0.30;

    private final CareerRequiredSkillRepository careerRequiredSkillRepository;
    private final StudentSkillRepository studentSkillRepository;

    /**
     * @param level         after the ceiling — what the student is shown
     * @param rawLevel      before it, so the gap is explainable
     * @param ratioAll      share of required skills held, declared or verified
     * @param ratioVerified share held with an objective source behind it
     * @param requiredCount the denominator
     * @param heldCount     numerator of {@code ratioAll}, carried as a count so the
     *                      UI can say "11 of 29" instead of re-deriving it from a
     *                      rounded percentage and landing a skill off
     * @param verifiedCount numerator of {@code ratioVerified}, same reason
     */
    public record SeniorityVerdict(SeniorityLevel level,
                                   SeniorityLevel rawLevel,
                                   BigDecimal ratioAll,
                                   BigDecimal ratioVerified,
                                   int requiredCount,
                                   int heldCount,
                                   int verifiedCount) {}

    public SeniorityVerdict compute(UUID userId, UUID careerId) {
        List<CareerRequiredSkill> required = careerRequiredSkillRepository
                .findByCareerRole_CareerIdAndImportanceLevelIn(careerId, CORE_IMPORTANCE);
        int requiredCount = required.size();

        // No target data means no basis for a level. BEGINNER is the honest floor
        // here, not a judgement about the student.
        if (requiredCount == 0) {
            log.warn("SeniorityCalculator: career {} has no required skills; defaulting to BEGINNER.", careerId);
            return new SeniorityVerdict(SeniorityLevel.BEGINNER, SeniorityLevel.BEGINNER,
                    BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0);
        }

        Set<UUID> requiredSkillIds = new HashSet<>();
        for (CareerRequiredSkill crs : required) {
            if (crs.getSkill() != null && crs.getSkill().getSkillId() != null) {
                requiredSkillIds.add(crs.getSkill().getSkillId());
            }
        }

        // The guard above counts rows; the ratios below divide by distinct skills.
        // Rows whose skill was deleted (skill_id is ON DELETE SET NULL) leave the
        // first non-zero and the second zero, and 0.0/0 is NaN — which slips past
        // every band in bandOf() and then throws out of BigDecimal.valueOf(NaN).
        if (requiredSkillIds.isEmpty()) {
            log.warn("SeniorityCalculator: career {} has {} required row(s) but none resolve to a "
                    + "skill; defaulting to BEGINNER.", careerId, requiredCount);
            return new SeniorityVerdict(SeniorityLevel.BEGINNER, SeniorityLevel.BEGINNER,
                    BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0);
        }

        Map<UUID, StudentSkill> held = new HashMap<>();
        for (StudentSkill ss : studentSkillRepository.findByStudent_UserId(userId)) {
            if (ss.getSkill() != null && ss.getSkill().getSkillId() != null) {
                held.put(ss.getSkill().getSkillId(), ss);
            }
        }

        int verified = 0;
        int declared = 0;
        for (UUID skillId : requiredSkillIds) {
            StudentSkill ss = held.get(skillId);
            if (ss == null) continue;
            // A row with no proficiency predates the assessment (or came from the
            // roadmap auto-sync). We have never asked how well they know it, so it
            // cannot count towards a level claim.
            if (ss.getProficiency() == null || ss.getProficiency() < COUNTS_AS_HELD) continue;
            if (ss.getVerifiedBy() != null && !ss.getVerifiedBy().isBlank()) {
                verified++;
            } else {
                declared++;
            }
        }

        double ratioAll = (double) (verified + declared) / requiredSkillIds.size();
        double ratioVerified = (double) verified / requiredSkillIds.size();

        SeniorityLevel rawLevel = bandOf(ratioAll);
        SeniorityLevel level = ratioVerified < VERIFIED_FLOOR
                ? SeniorityLevel.min(rawLevel, SeniorityLevel.JUNIOR)
                : rawLevel;

        log.debug("SeniorityCalculator: user {} career {} -> {} (raw {}), {} of {} required held "
                        + "({} verified).",
                userId, careerId, level, rawLevel, verified + declared, requiredSkillIds.size(), verified);

        return new SeniorityVerdict(level, rawLevel, round(ratioAll), round(ratioVerified),
                requiredSkillIds.size(), verified + declared, verified);
    }

    /**
     * One row per skill this career grades on, held or not.
     *
     * <p>Deliberately the same {@code CORE_IMPORTANCE} query {@link #compute} runs,
     * because the skill map draws the denominator behind the readiness figure and
     * the level badge. Building that list from a second source is how the dashboard
     * ended up showing "5/1466 mastered" beside "0 of 29 required skills" — one
     * question with two answers on one screen.
     *
     * <p>Missing skills are rows too, with {@code proficiency = null}. That is the
     * point of the map: a gap the student cannot see is a gap they cannot close,
     * and an absent bubble says nothing while an empty one says "this is yours to
     * take".
     */
    public List<CoreSkill> coreSkills(UUID userId, UUID careerId) {
        List<CareerRequiredSkill> required = careerRequiredSkillRepository
                .findByCareerRole_CareerIdAndImportanceLevelIn(careerId, CORE_IMPORTANCE);
        if (required.isEmpty()) {
            return List.of();
        }

        Map<UUID, StudentSkill> held = new HashMap<>();
        for (StudentSkill ss : studentSkillRepository.findByStudent_UserId(userId)) {
            if (ss.getSkill() != null && ss.getSkill().getSkillId() != null) {
                held.put(ss.getSkill().getSkillId(), ss);
            }
        }

        Map<UUID, CoreSkill> bySkillId = new LinkedHashMap<>();
        for (CareerRequiredSkill crs : required) {
            if (crs.getSkill() == null || crs.getSkill().getSkillId() == null) {
                continue;
            }
            UUID skillId = crs.getSkill().getSkillId();
            // One row per skill: a career can list the same skill twice and the map
            // must not draw two bubbles on top of each other.
            if (bySkillId.containsKey(skillId)) {
                continue;
            }
            StudentSkill ss = held.get(skillId);
            Short proficiency = ss == null ? null : ss.getProficiency();
            // Below COUNTS_AS_HELD the level does not count it, so the map must not
            // colour it in either — the two views have to agree about what "have" means.
            boolean counts = proficiency != null && proficiency >= COUNTS_AS_HELD;
            bySkillId.put(skillId, new CoreSkill(
                    skillId,
                    crs.getSkill().getSkillName(),
                    crs.getImportanceLevel() == null ? null : crs.getImportanceLevel().name(),
                    counts ? proficiency : null,
                    counts && ss.getVerifiedBy() != null && !ss.getVerifiedBy().isBlank()
                            ? ss.getVerifiedBy() : null));
        }
        return List.copyOf(bySkillId.values());
    }

    /**
     * @param proficiency 1..4, or null when the student does not hold it at a level
     *        that counts — which is what the map draws as a hollow bubble
     * @param verifiedBy GITHUB | TRANSCRIPT | MENTOR, null for a self-report
     */
    public record CoreSkill(UUID skillId, String skillName, String importance,
                            Short proficiency, String verifiedBy) {
        public boolean held() {
            return proficiency != null;
        }
    }

    private SeniorityLevel bandOf(double ratioAll) {
        if (ratioAll >= EXPERT_AT) return SeniorityLevel.EXPERT;
        if (ratioAll >= SENIOR_AT) return SeniorityLevel.SENIOR;
        if (ratioAll >= MID_AT) return SeniorityLevel.MID;
        if (ratioAll >= JUNIOR_AT) return SeniorityLevel.JUNIOR;
        if (ratioAll >= FRESHER_AT) return SeniorityLevel.FRESHER;
        return SeniorityLevel.BEGINNER;
    }

    private BigDecimal round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
