package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.ai.analyzer.PortfolioAiAnalyzer.SkillMatch;
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
    List<String> careerSkillCatalog(UUID careerId);

    /**
     * Persists evidence for the AI-matched skills of a source (deduping against
     * skills the user already has evidence for). Only skills that exist in the
     * catalog/skills table are recorded.
     */
    void recordEvidence(UUID userId, List<SkillMatch> matches, EvidenceType sourceType, UUID sourceId);

    /**
     * Records the provenance of skills the student declared about themselves during
     * self-assessment. Without this, a self-declared skill is indistinguishable from
     * one proven by a GitHub repo or a passed subject once it lands in student_skills.
     * Written as ACCEPTED (the skill is already on the profile) but at a deliberately
     * low confidence, so it cannot fast-track anything a real source could.
     */
    void recordSelfDeclaredEvidence(UUID userId, List<Skill> skills);
}
