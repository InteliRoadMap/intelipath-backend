package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.domain.dto.response.market.SkillDemandResponse;
import com.inteliroadmap.backend.domain.enums.ImportanceLevel;
import com.inteliroadmap.backend.domain.enums.SeniorityLevel;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything about one student that can change the order of their roadmap.
 *
 * <p>Gathered once per request and passed to {@link RoadmapEdgeResolver}, which
 * is a pure function of it — that is what makes the ordering testable without a
 * database, and what lets the same context be reused for the unlock pass and the
 * display pass so the two can never disagree.
 *
 * @param proficiencyBySkillId 1..4 per skill the student holds, from {@code student_skills}
 * @param heldSkillNamesLower lowercased skill names, the fallback path for the
 *        671 nodes that carry no {@code skill_id}
 * @param level nullable — a student who skipped the assessment has no level at
 *        all, which is not FRESHER, and readiness scoring switches itself off
 * @param demandBySkill market frequency per skill; empty when the scraper has
 *        never run
 * @param importanceBySkillId how much the target career needs each skill
 */
public record StudentRoadmapContext(
        Map<UUID, Short> proficiencyBySkillId,
        Set<String> heldSkillNamesLower,
        SeniorityLevel level,
        Map<UUID, SkillDemandResponse> demandBySkill,
        Map<UUID, ImportanceLevel> importanceBySkillId,
        double maxRelevance) {

    /**
     * Keeps the five-argument shape every caller already builds, and derives the
     * scale the demand term needs from the map they pass.
     */
    public StudentRoadmapContext(Map<UUID, Short> proficiencyBySkillId,
                                 Set<String> heldSkillNamesLower,
                                 SeniorityLevel level,
                                 Map<UUID, SkillDemandResponse> demandBySkill,
                                 Map<UUID, ImportanceLevel> importanceBySkillId) {
        this(proficiencyBySkillId, heldSkillNamesLower, level, demandBySkill, importanceBySkillId,
                maxRelevanceOf(demandBySkill));
    }

    /** A student we know nothing about: ordering falls back to the static one. */
    public static StudentRoadmapContext empty() {
        return new StudentRoadmapContext(Map.of(), Set.of(), null, Map.of(), Map.of());
    }

    /**
     * The largest TF-IDF relevance in this career, used to put the demand term on
     * the same 0..1 footing as importance and readiness.
     *
     * Relevance is {@code frequency × importanceWeight × ln(N / (df + 1))}, which
     * on the current 866 postings peaks around 0.06 and sits at 0.005 in the
     * median. Fed raw into a 0.3-weighted sum it moved the priority by under one
     * part in fifty — the market factor was present in the formula and absent
     * from the result. Dividing by the career's own maximum is what makes
     * "priority reflects demand" a true statement rather than a stated intention.
     */
    private static double maxRelevanceOf(Map<UUID, SkillDemandResponse> demandBySkill) {
        if (demandBySkill == null || demandBySkill.isEmpty()) {
            return 0;
        }
        double max = 0;
        for (SkillDemandResponse demand : demandBySkill.values()) {
            if (demand != null && demand.getRelevance() != null && demand.getRelevance() > max) {
                max = demand.getRelevance();
            }
        }
        return max;
    }
}
