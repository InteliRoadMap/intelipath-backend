package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.ai.SkillMatch;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.enums.EvidenceType;

import java.util.List;
import java.util.UUID;

/**
 * Turns AI-detected skills (e.g. from a GitHub repo) into student_skill_evidence
 * rows that the roadmap personalization engine can use to suggest shortcuts.
 * Evidence is always PENDING - the student still has to accept the shortcut.
 */
public interface SkillEvidenceService {

    /** Skill names required by a career, to hand the AI as the matching catalog. */
    /**
     * Every skill of the career, ordered by market demand.
     *
     * <p>Returned whole. Callers that need to fit a prompt must choose which names are
     * worth the space using what they know about the thing being analysed — truncating
     * this list by rank runs alphabetically past the point where market data stops.
     */
    List<String> careerSkillCatalog(UUID careerId);

    /**
     * Persists evidence for the AI-matched skills of an objective source — an
     * analysed repository or a transcript. Only skills that exist in the
     * catalog/skills table are recorded.
     *
     * <p>An earlier self-report for the same skill is <em>superseded</em>, not
     * treated as a reason to skip. The previous behaviour deduped against any
     * live evidence whatever its source, and the skill-selection screen writes an
     * ACCEPTED {@code MANUAL} row for every skill a student ticks — so declaring
     * "Java" at onboarding silently blocked a Java repository from ever recording
     * GitHub evidence for it. {@code verified_by} then stayed null, the verified
     * share stayed at zero, and {@code SeniorityCalculator.VERIFIED_FLOOR} capped
     * the student at JUNIOR permanently. It failed hardest on exactly the skills
     * the student cared enough to declare.
     *
     * @param sourceUrl where the evidence came from (a repository URL), so a
     *                  re-import can be traced back to what produced it
     * @return ids of the rows just written, for
     *         {@code SkillProficiencyPromoter.promoteFromEvidence}
     */
    List<UUID> recordEvidence(UUID userId, List<SkillMatch> matches, EvidenceType sourceType,
                              UUID sourceId, String sourceUrl);

    /**
     * Deletes every row one source produced, and reports which skills lost their backing.
     *
     * <p>Already used internally before a re-import, where withdrawal is invisible because
     * fresh rows immediately replace the old ones. Exposed because deleting the portfolio
     * project has no such replacement: the student is giving up the claim, and someone has
     * to be able to say so on their behalf.
     *
     * <p>The returned names are what the caller owes the student an account of — the level
     * is about to move, and "which skills" is the only version of that answer they can act
     * on.
     *
     * @return skill names whose rows were deleted, in no particular order; empty when the
     *         source had nothing recorded
     */
    List<String> withdrawEvidenceFrom(UUID userId, EvidenceType sourceType, String sourceUrl);

    /**
     * Records the provenance of skills the student declared about themselves during
     * self-assessment. Without this, a self-declared skill is indistinguishable from
     * one proven by a GitHub repo or a passed subject once it lands in student_skills.
     * Written as ACCEPTED (the skill is already on the profile) but at a deliberately
     * low confidence, so it cannot fast-track anything a real source could.
     */
    void recordSelfDeclaredEvidence(UUID userId, List<Skill> skills);

    /**
     * Records evidence produced by the AI-graded career self-assessment.
     *
     * <p>Separate from {@link #recordEvidence} because the assessment is
     * explicitly re-takeable and that method deduplicates by skipping: it drops
     * any skill the student already has evidence for. Since the manual skill
     * screen writes a self-report row for every skill the student ticks, routing
     * the assessment through it would discard every result — a second run would
     * appear to succeed and change nothing.
     *
     * <p>This method supersedes instead. A new, better-supported claim about a
     * skill replaces the weaker self-declaration it supersedes; evidence from a
     * stronger source (an analysed repository, a passed subject) is never
     * touched, whatever the assessment says.
     *
     * @param assessmentId the run this evidence came from, stored as the source id
     */
    /**
     * @return ids of the freshly recorded rows, for
     *         {@code SkillProficiencyPromoter.promoteFromEvidence} to weigh and settle
     */
    List<UUID> recordAssessmentEvidence(UUID userId, List<SkillMatch> matches, UUID assessmentId);
}
