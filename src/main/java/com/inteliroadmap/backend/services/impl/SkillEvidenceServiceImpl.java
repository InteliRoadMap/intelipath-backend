package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.ai.analyzer.PortfolioAiAnalyzer.SkillMatch;
import com.inteliroadmap.backend.domain.entity.CareerRequiredSkill;
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
import java.util.HashSet;
import java.util.List;
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

    private final CareerRequiredSkillRepository careerRequiredSkillRepository;
    private final SkillRepository skillRepository;
    private final StudentSkillEvidenceRepository evidenceRepository;

    @Transactional
    @Override
    public List<String> careerSkillCatalog(UUID careerId) {
        if (careerId == null) {
            return List.of();
        }
        return careerRequiredSkillRepository.findByCareerRole_CareerId(careerId).stream()
                .map(CareerRequiredSkill::getSkill)
                .filter(skill -> skill != null && skill.getSkillName() != null)
                .map(Skill::getSkillName)
                .distinct()
                .toList();
    }

    @Transactional
    @Override
    public void recordEvidence(UUID userId, List<SkillMatch> matches, EvidenceType sourceType, UUID sourceId) {
        if (userId == null || matches == null || matches.isEmpty()) {
            return;
        }

        // Skills the user already has any evidence for - don't pile on duplicates.
        Set<String> alreadyEvidenced = new HashSet<>();
        for (StudentSkillEvidence existing : evidenceRepository.findByUserIdAndStatusIn(
                userId, List.of(EvidenceStatus.PENDING, EvidenceStatus.ACCEPTED))) {
            if (existing.getSkillName() != null) {
                alreadyEvidenced.add(existing.getSkillName().toLowerCase());
            }
        }

        int recorded = 0;
        for (SkillMatch match : matches) {
            if (match == null || match.skill() == null || match.skill().isBlank()) {
                continue;
            }
            BigDecimal confidence = clampConfidence(match.confidence());
            if (confidence.compareTo(MIN_RECORDABLE_CONFIDENCE) < 0) {
                continue;
            }
            // The AI must map to a real skill; ignore hallucinated names. Case-insensitive
            // because the LLM's casing (e.g. "reactjs" vs "ReactJS") doesn't always match the
            // catalog exactly even when it picked the right skill.
            Skill skill = skillRepository.findBySkillNameIgnoreCase(match.skill().trim());
            if (skill == null) {
                log.warn("SkillEvidenceServiceImpl: AI-matched skill '{}' has no catalog entry; discarding", match.skill());
                continue;
            }
            if (alreadyEvidenced.contains(skill.getSkillName().toLowerCase())) {
                continue;
            }

            evidenceRepository.save(StudentSkillEvidence.builder()
                    .userId(userId)
                    .skillName(skill.getSkillName())
                    .sourceType(sourceType)
                    .sourceId(sourceId)
                    .confidence(confidence)
                    .detectedBy("ai-service")
                    .detectedAt(LocalDateTime.now())
                    .status(EvidenceStatus.PENDING)
                    .build());
            alreadyEvidenced.add(skill.getSkillName().toLowerCase());
            recorded++;
        }

        if (recorded > 0) {
            log.info("SkillEvidenceServiceImpl: Recorded {} {} evidence row(s) for user {}", recorded, sourceType, userId);
        }
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

    private BigDecimal clampConfidence(double raw) {
        BigDecimal value = BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP);
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return value.min(MAX_EVIDENCE_CONFIDENCE);
    }
}
