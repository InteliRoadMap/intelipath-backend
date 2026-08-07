package com.inteliroadmap.backend.domain.dto.internal.portfolio;

import com.inteliroadmap.backend.domain.enums.ImportanceLevel;

/** Evidence-aware skill input used only when drafting portfolio copy. */
public record PortfolioAboutSkillFact(
        String skillName,
        ImportanceLevel careerImportance,
        Short proficiency,
        String verifiedBy,
        long careerPostingCount
) {
    public boolean verified() {
        return verifiedBy != null && !verifiedBy.isBlank();
    }

    public String promptLine() {
        return "%s [importance=%s, proficiency=%s/4, verified=%s, backend-postings=%d]".formatted(
                skillName,
                careerImportance == null ? "NOT_REQUIRED" : careerImportance,
                proficiency == null ? "unknown" : proficiency,
                verified() ? verifiedBy : "no",
                careerPostingCount);
    }
}
