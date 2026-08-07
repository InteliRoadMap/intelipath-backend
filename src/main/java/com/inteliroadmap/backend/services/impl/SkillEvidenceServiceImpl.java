package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.components.SkillNameCanonicalizer;
import com.inteliroadmap.backend.components.SkillProficiencyPromoter;
import com.inteliroadmap.backend.domain.dto.ai.SkillMatch;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.entity.StudentSkillEvidence;
import com.inteliroadmap.backend.domain.enums.EvidenceStatus;
import com.inteliroadmap.backend.domain.enums.EvidenceType;
import com.inteliroadmap.backend.repositories.CareerRequiredSkillRepository;
import com.inteliroadmap.backend.repositories.SkillRepository;
import com.inteliroadmap.backend.repositories.StudentSkillEvidenceRepository;
import com.inteliroadmap.backend.services.SkillEvidenceService;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SkillEvidenceServiceImpl implements SkillEvidenceService {

    // Portfolio evidence is self-directed, so it never counts as certainty.
    private static final BigDecimal MAX_EVIDENCE_CONFIDENCE = new BigDecimal("0.90");
    private static final BigDecimal MIN_RECORDABLE_CONFIDENCE = new BigDecimal("0.40");

    /**
     * A student ticking a box is the weakest source we have - below the FLM transcript
     * base (0.72) and well below an AI-analysed repository. Kept in step with
     * RoadmapPersonalizationServiceImpl.PROFILE_SKILL_CONFIDENCE so the same claim
     * carries the same weight whichever of the two paths the engine reads it from.
     */
    private static final BigDecimal SELF_REPORT_CONFIDENCE = new BigDecimal("0.60");
    private static final String SELF_REPORT_DETECTED_BY = "student-self-report";

    /**
     * Provenance tag for the AI-graded self-assessment, and the ceiling its
     * confidence is clamped to.
     *
     * <p>0.80 sits below {@link #MAX_EVIDENCE_CONFIDENCE} (an analysed repository)
     * and below the HIGH-importance floor in RoadmapPersonalizationServiceImpl
     * (0.85). That gap is the safety property of the whole feature: because the
     * assessment applies to the roadmap without asking, it must not be able to
     * clear a foundational node on the strength of a questionnaire.
     */
    private static final String ASSESSMENT_DETECTED_BY = "self-assessment";
    private static final BigDecimal MAX_ASSESSMENT_CONFIDENCE = new BigDecimal("0.80");

    /**
     * No cap here any more. The cap belongs to whoever builds a prompt.
     *
     * <p>This method used to truncate at 200, which fixed one bug and hid another. The
     * bug it fixed was real: handed all 1,466 names for a career, gpt-4o-mini returned an
     * empty list for three consecutive Spring Boot repositories and nothing in the
     * student's profile moved.
     *
     * <p>The bug it hid was that past roughly the sixtieth name nothing has market demand
     * behind it, every remaining row ties at zero postings, and the query's last
     * tie-break is the skill's name — so the truncated list ran alphabetically. Measured
     * on Backend: 60 of the 200 had posting data and the other 140 were the
     * alphabetically earliest of 1,374 leftovers, while Maven (rank 881), JWT (796) and
     * Hibernate (729) never reached the model at all.
     *
     * <p>Truncating by rank cannot fix that, because the ranking has nothing left to say.
     * Choosing which names are worth a prompt slot needs to know what the repository
     * actually contains, which is knowledge this service does not have and the caller
     * does — see {@code RepoSkillCandidateSelector}.
     */
    private static final int NO_LIMIT = Integer.MAX_VALUE;

    private final CareerRequiredSkillRepository careerRequiredSkillRepository;
    private final SkillRepository skillRepository;
    private final StudentSkillEvidenceRepository evidenceRepository;
    private final SkillProficiencyPromoter skillProficiencyPromoter;
    private final SkillNameCanonicalizer skillNameCanonicalizer;

    @Transactional
    @Override
    public List<String> careerSkillCatalog(UUID careerId) {
        if (careerId == null) {
            return List.of();
        }
        return careerRequiredSkillRepository.findRankedSkillNames(careerId, NO_LIMIT).stream()
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
    }

    /**
     * Deletes the rows a previous analysis of this exact source produced.
     *
     * <p>Deleted rather than marked REJECTED. REJECTED means "a stronger claim beat this
     * one" and is shown to the student as such; a superseded re-read of the same
     * repository was never beaten by anything, and leaving those rows behind would fill
     * the audit screen with a history of the system talking to itself.
     *
     * <p>Does nothing when the source has no URL to match on — a null URL identifies
     * nothing, and treating it as a key would let one sourceless import wipe another's
     * evidence.
     */
    private void withdrawPreviousEvidenceFrom(UUID userId, EvidenceType sourceType, String sourceUrl) {
        List<String> withdrawn = deleteEvidenceFrom(userId, sourceType, sourceUrl);
        if (!withdrawn.isEmpty()) {
            log.info("SkillEvidenceServiceImpl: withdrew {} earlier {} evidence row(s) from {} before re-recording",
                    withdrawn.size(), sourceType, sourceUrl);
        }
        // No verifier is revoked here on purpose: fresh rows for this very source land
        // milliseconds from now, and revoking a verification that is about to be
        // re-granted would make a re-import look like a demotion in the logs and in any
        // screen that happened to read between the two writes.
    }

    /** @return the skill names the deleted rows named, in row order. */
    private List<String> deleteEvidenceFrom(UUID userId, EvidenceType sourceType, String sourceUrl) {
        if (userId == null || sourceUrl == null || sourceUrl.isBlank()) {
            return List.of();
        }
        List<StudentSkillEvidence> previous = evidenceRepository
                .findByUserIdAndSourceUrl(userId, sourceUrl).stream()
                .filter(row -> row.getSourceType() == sourceType)
                .toList();
        if (previous.isEmpty()) {
            return List.of();
        }
        List<String> names = previous.stream()
                .map(StudentSkillEvidence::getSkillName)
                .filter(name -> name != null && !name.isBlank())
                .toList();
        evidenceRepository.deleteAll(previous);
        return names;
    }

    @Transactional
    @Override
    public List<String> withdrawEvidenceFrom(UUID userId, EvidenceType sourceType, String sourceUrl) {
        List<String> withdrawn = deleteEvidenceFrom(userId, sourceType, sourceUrl);
        if (withdrawn.isEmpty()) {
            return List.of();
        }
        // Deleting the rows is only half of it. The verifier was copied onto
        // student_skills when the evidence landed, and that copy — not the evidence — is
        // what the level actually reads. Leaving it behind would delete the proof and
        // keep the claim.
        int revoked = skillProficiencyPromoter.revokeVerification(userId, withdrawn);
        log.info("SkillEvidenceServiceImpl: withdrew {} {} evidence row(s) from {} at the student's request; "
                + "{} skill row(s) lost their verifier", withdrawn.size(), sourceType, sourceUrl, revoked);
        return withdrawn;
    }

    /** True when this row came from the same source that is being re-recorded. */
    private boolean isSameSource(StudentSkillEvidence row, EvidenceType sourceType, String sourceUrl) {
        return sourceUrl != null && !sourceUrl.isBlank()
                && row.getSourceType() == sourceType
                && sourceUrl.equalsIgnoreCase(row.getSourceUrl());
    }

    @Transactional
    @Override
    public List<UUID> recordEvidence(UUID userId, List<SkillMatch> matches, EvidenceType sourceType,
                                     UUID sourceId, String sourceUrl) {
        if (userId == null || matches == null || matches.isEmpty()) {
            return List.of();
        }

        // Rows this very source produced last time are withdrawn before anything else,
        // because a re-analysis replaces its own previous answer rather than competing
        // with it.
        //
        // Without this, re-importing a repository was a no-op that looked like a
        // failure. Its own earlier rows sat in the guard below as accepted, objective
        // evidence, so every skill hit `continue`, nothing was recorded, nothing was
        // promoted, and the student saw an import that changed nothing with no
        // explanation. Deleting the project from the portfolio and adding it back —
        // an ordinary thing to do — left them permanently unable to re-import it,
        // because the evidence outlived the project it came from.
        //
        // Confined to an exact source match. Evidence from any OTHER repository is
        // still a competing claim and is still handled by the guard below.
        withdrawPreviousEvidenceFrom(userId, sourceType, sourceUrl);

        // Indexed by skill rather than collected into a "seen" set, because what an
        // existing row says matters: another objective source means there is nothing
        // to add, while a self-report is exactly what this evidence should replace.
        Map<String, StudentSkillEvidence> existingBySkill = new HashMap<>();
        for (StudentSkillEvidence existing : evidenceRepository.findByUserIdAndStatusIn(
                userId, List.of(EvidenceStatus.PENDING, EvidenceStatus.ACCEPTED))) {
            if (existing.getSkillName() != null && !isSameSource(existing, sourceType, sourceUrl)) {
                existingBySkill.putIfAbsent(existing.getSkillName().toLowerCase(), existing);
            }
        }

        // Kept apart so the ids can come from what saveAll returns rather than from
        // Hibernate having filled them in on the instances we passed it.
        List<StudentSkillEvidence> supersededRows = new ArrayList<>();
        List<StudentSkillEvidence> freshRows = new ArrayList<>();

        for (SkillMatch match : matches) {
            if (match == null || match.skill() == null || match.skill().isBlank()) {
                continue;
            }
            BigDecimal confidence = clampConfidence(match.confidence());
            if (confidence.compareTo(MIN_RECORDABLE_CONFIDENCE) < 0) {
                continue;
            }
            // The AI must map to a real skill; ignore hallucinated names. Resolved through
            // the canonicaliser because the model writes whatever the repository called it
            // — "reactjs", "React.js", "Fast API" — and a case-insensitive string compare
            // discarded a correct answer whenever the spelling differed by a space or a
            // dot, silently costing the student evidence they had earned.
            Skill skill = skillNameCanonicalizer.resolve(match.skill().trim());
            if (skill == null) {
                log.warn("SkillEvidenceServiceImpl: AI-matched skill '{}' has no catalog entry; discarding", match.skill());
                continue;
            }

            StudentSkillEvidence existing = existingBySkill.get(skill.getSkillName().toLowerCase());
            if (existing != null) {
                // Another repository or a transcript already stands for this skill;
                // a second one adds nothing the level can use.
                if (!isSelfReported(existing)) {
                    continue;
                }
                // A read of the student's actual work outranks their own account of
                // it regardless of the numbers, so unlike the assessment path this
                // does not compare confidences — a 0.60 self-report never blocks a
                // 0.45 repository read, because they are not the same kind of claim.
                existing.setStatus(EvidenceStatus.REJECTED);
                existing.setEvidenceText(appendSupersededNote(existing.getEvidenceText(), sourceType, sourceUrl));
                supersededRows.add(existing);
            }

            StudentSkillEvidence fresh = StudentSkillEvidence.builder()
                    .userId(userId)
                    .skillName(skill.getSkillName())
                    .sourceType(sourceType)
                    .sourceId(sourceId)
                    .sourceUrl(sourceUrl)
                    .confidence(confidence)
                    .detectedBy("ai-service")
                    .detectedAt(LocalDateTime.now())
                    .status(EvidenceStatus.PENDING)
                    .build();
            freshRows.add(fresh);
            // Keep the index honest so a duplicate skill name inside one batch does
            // not supersede the row this same loop just created.
            existingBySkill.put(skill.getSkillName().toLowerCase(), fresh);
        }

        if (!supersededRows.isEmpty()) {
            evidenceRepository.saveAll(supersededRows);
        }
        if (freshRows.isEmpty()) {
            return List.of();
        }

        List<UUID> evidenceIds = new ArrayList<>();
        for (StudentSkillEvidence saved : evidenceRepository.saveAll(freshRows)) {
            if (saved.getEvidenceId() != null) {
                evidenceIds.add(saved.getEvidenceId());
            }
        }
        log.info("SkillEvidenceServiceImpl: Recorded {} {} evidence row(s) for user {} "
                        + "({} self-report(s) superseded)",
                evidenceIds.size(), sourceType, userId, supersededRows.size());
        return evidenceIds;
    }

    private String appendSupersededNote(String existingText, EvidenceType sourceType, String sourceUrl) {
        String note = "Superseded by " + sourceType + (sourceUrl == null ? "" : " " + sourceUrl);
        return (existingText == null || existingText.isBlank()) ? note : existingText + " | " + note;
    }

    @Transactional
    @Override
    public void recordSelfDeclaredEvidence(UUID userId, List<Skill> skills) {
        if (userId == null || skills == null || skills.isEmpty()) {
            return;
        }

        // Any live evidence for the skill already says more than a self-declaration would.
        Set<String> alreadyEvidenced = new HashSet<>();
        for (StudentSkillEvidence existing : evidenceRepository.findByUserIdAndStatusIn(
                userId, List.of(EvidenceStatus.PENDING, EvidenceStatus.ACCEPTED))) {
            if (existing.getSkillName() != null) {
                alreadyEvidenced.add(existing.getSkillName().toLowerCase());
            }
        }

        List<StudentSkillEvidence> toCreate = new ArrayList<>();
        for (Skill skill : skills) {
            if (skill == null || skill.getSkillName() == null || skill.getSkillName().isBlank()) {
                continue;
            }
            if (!alreadyEvidenced.add(skill.getSkillName().toLowerCase())) {
                continue;
            }
            toCreate.add(StudentSkillEvidence.builder()
                    .userId(userId)
                    .skillName(skill.getSkillName())
                    .sourceType(EvidenceType.MANUAL)
                    .evidenceText("Declared by the student during skill self-assessment")
                    .confidence(SELF_REPORT_CONFIDENCE)
                    .detectedBy(SELF_REPORT_DETECTED_BY)
                    .detectedAt(LocalDateTime.now())
                    // ACCEPTED, not PENDING: the skill is already on the profile, so there
                    // is nothing left for the student to approve. The low confidence, not
                    // the status, is what keeps an unproven claim from being over-trusted.
                    .status(EvidenceStatus.ACCEPTED)
                    .build());
        }

        if (!toCreate.isEmpty()) {
            evidenceRepository.saveAll(toCreate);
            log.info("SkillEvidenceServiceImpl: Recorded {} self-declared evidence row(s) for user {}",
                    toCreate.size(), userId);
        }
    }

    @Transactional
    @Override
    public List<UUID> recordAssessmentEvidence(UUID userId, List<SkillMatch> matches, UUID assessmentId) {
        if (userId == null || matches == null || matches.isEmpty()) {
            return List.of();
        }

        // Index live evidence by skill name so each new claim can be compared
        // against whatever already stands for that skill.
        Map<String, StudentSkillEvidence> existingBySkill = new HashMap<>();
        for (StudentSkillEvidence existing : evidenceRepository.findByUserIdAndStatusIn(
                userId, List.of(EvidenceStatus.PENDING, EvidenceStatus.ACCEPTED))) {
            if (existing.getSkillName() != null) {
                existingBySkill.putIfAbsent(existing.getSkillName().toLowerCase(), existing);
            }
        }

        List<StudentSkillEvidence> toSave = new ArrayList<>();
        // Tracked apart from `toSave`, which also carries the superseded rows: only
        // the fresh claims are handed on to be promoted and settled.
        List<StudentSkillEvidence> freshRows = new ArrayList<>();
        int superseded = 0;

        for (SkillMatch match : matches) {
            if (match == null || match.skill() == null || match.skill().isBlank()) {
                continue;
            }
            BigDecimal confidence = clampAssessmentConfidence(match.confidence());
            if (confidence.compareTo(MIN_RECORDABLE_CONFIDENCE) < 0) {
                continue;
            }
            // Same anti-hallucination filter as the AI path: a name with no catalog
            // row is discarded rather than minted into a new skill.
            Skill skill = skillNameCanonicalizer.resolve(match.skill().trim());
            if (skill == null) {
                log.warn("SkillEvidenceServiceImpl: assessed skill '{}' has no catalog entry; discarding",
                        match.skill());
                continue;
            }

            StudentSkillEvidence existing = existingBySkill.get(skill.getSkillName().toLowerCase());
            if (existing != null) {
                // Keyed on the source, not the status. The manual skill screen writes
                // its rows as ACCEPTED, so gating on PENDING would protect exactly the
                // rows this method exists to replace. What actually matters is that a
                // ticked box and a graded questionnaire are both the student talking
                // about themselves — the better-supported one should win — whereas an
                // analysed repository or a passed subject is a different kind of claim
                // and outranks both no matter how confident the assessment is.
                if (!isSelfReported(existing)) {
                    continue;
                }
                if (existing.getConfidence() != null
                        && existing.getConfidence().compareTo(confidence) >= 0) {
                    continue;
                }
                existing.setStatus(EvidenceStatus.REJECTED);
                existing.setEvidenceText(appendSupersededNote(existing.getEvidenceText(), assessmentId));
                toSave.add(existing);
                superseded++;
            }

            StudentSkillEvidence fresh = StudentSkillEvidence.builder()
                    .userId(userId)
                    .skillName(skill.getSkillName())
                    .sourceType(EvidenceType.MANUAL)
                    .sourceId(assessmentId)
                    .evidenceText(match.skill() + ": graded from the student's career self-assessment")
                    .confidence(confidence)
                    .detectedBy(ASSESSMENT_DETECTED_BY)
                    .detectedAt(LocalDateTime.now())
                    // Born PENDING and settled by the caller once it has been weighed,
                    // exactly like the repository path. It used to be left PENDING for
                    // the personalization engine to close, and the engine only ever
                    // closed rows attached to a node it had just completed — so a claim
                    // about anything the roadmap does not model stayed open for good.
                    .status(EvidenceStatus.PENDING)
                    .build();
            toSave.add(fresh);
            freshRows.add(fresh);
        }

        if (!toSave.isEmpty()) {
            evidenceRepository.saveAll(toSave);
            log.info("SkillEvidenceServiceImpl: Recorded assessment evidence for user {} "
                            + "({} new row(s), {} earlier self-report(s) superseded)",
                    userId, toSave.size() - superseded, superseded);
        }
        return freshRows.stream().map(StudentSkillEvidence::getEvidenceId).filter(java.util.Objects::nonNull).toList();
    }

    /** True for rows the student wrote about themselves, whatever their status. */
    private boolean isSelfReported(StudentSkillEvidence evidence) {
        String detectedBy = evidence.getDetectedBy();
        return SELF_REPORT_DETECTED_BY.equals(detectedBy) || ASSESSMENT_DETECTED_BY.equals(detectedBy);
    }

    private String appendSupersededNote(String existingText, UUID assessmentId) {
        String note = "Superseded by self-assessment " + assessmentId;
        return (existingText == null || existingText.isBlank()) ? note : existingText + " | " + note;
    }

    /**
     * Assessment confidence is capped below the AI-read-a-repository ceiling and,
     * critically, below the HIGH-importance floor the personalization engine
     * applies. The consequence is deliberate: a self-assessment can move a student
     * past secondary skills, but never past a foundational one — those still need
     * a repository or a transcript behind them.
     */
    private BigDecimal clampAssessmentConfidence(double raw) {
        BigDecimal value = BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP);
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return value.min(MAX_ASSESSMENT_CONFIDENCE);
    }

    private BigDecimal clampConfidence(double raw) {
        BigDecimal value = BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP);
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return value.min(MAX_EVIDENCE_CONFIDENCE);
    }
}
