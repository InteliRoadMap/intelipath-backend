package com.inteliroadmap.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One skill mentioned by one job posting.
 *
 * <p>The extraction pipeline has always known this — {@code extractSkills} returns a
 * list per description — and has always discarded it, aggregating straight into
 * {@code skill_trends} as a single market-wide count per skill per day. That is why
 * "which skills do Backend postings ask for" could not be answered without paying an
 * AI service to read all 913 descriptions over again, and why
 * {@code career_required_skills} ended up hand-written and inconsistent.
 *
 * <p>Kept flat and dumb on purpose: ids only, no owning-side relationships. The rows
 * are written in bulk after an extraction and read by aggregate queries; mapping them
 * as associations would load a posting and its skills one at a time for no benefit.
 */
@Entity
@Table(name = "recruitment_skills")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecruitmentSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "link_id")
    private UUID linkId;

    @Column(name = "recruitment_id", nullable = false)
    private String recruitmentId;

    @Column(name = "skill_id", nullable = false)
    private UUID skillId;

    @Column(name = "extracted_at", nullable = false)
    private LocalDateTime extractedAt;
}
