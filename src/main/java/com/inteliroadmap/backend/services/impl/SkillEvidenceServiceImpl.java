package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.components.PortfolioAiAnalyzer.SkillMatch;
import com.inteliroadmap.backend.domain.entity.CareerRequiredSkill;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.entity.StudentSkillEvidence;
import com.inteliroadmap.backend.domain.enums.EvidenceStatus;
import com.inteliroadmap.backend.domain.enums.EvidenceType;
import com.inteliroadmap.backend.repositories.CareerRequiredSkillRepository;
import com.inteliroadmap.backend.repositories.SkillRepository;
import com.inteliroadmap.backend.repositories.StudentSkillEvidenceRepository;
import com.inteliroadmap.backend.services.SkillEvidenceService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
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
            // The AI must map to a real skill; ignore hallucinated names.
            Skill skill = skillRepository.findBySkillName(match.skill().trim());
            if (skill == null || alreadyEvidenced.contains(skill.getSkillName().toLowerCase())) {
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

    private BigDecimal clampConfidence(double raw) {
        BigDecimal value = BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP);
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return value.min(MAX_EVIDENCE_CONFIDENCE);
    }
}
