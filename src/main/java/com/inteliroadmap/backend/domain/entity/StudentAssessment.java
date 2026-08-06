package com.inteliroadmap.backend.domain.entity;

import com.inteliroadmap.backend.domain.enums.SeniorityLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One run of the career self-assessment.
 *
 * <p>A run, not a profile field: the assessment is re-takeable (after connecting
 * GitHub, after a semester), the raw answers have to survive for the report, and
 * each run needs its own timestamp. Columns on {@code students} would keep only
 * the last one and destroy the history that makes the AI's verdict auditable.
 *
 * <p>Follows {@link StudentSkillEvidence}'s flat-{@code UUID} convention rather
 * than a {@code @ManyToOne Student}: the write path runs either side of a
 * multi-second AI call, and a lazy association across that boundary is a source
 * of surprises this table does not need.
 */
@Entity
@Table(name = "student_assessments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "assessment_id")
    private UUID assessmentId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "career_id", nullable = false)
    private UUID careerId;

    /**
     * Frozen copy of the questions this run served. The skill catalog is rebuilt
     * periodically, so without this a stored answer set could later reference a
     * skill id that no longer exists and become unreadable.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "questions", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> questions;

    /** {@code [{skillId, skillName, level, note}]} exactly as submitted. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answers", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> answers;

    /** The level after the JUNIOR ceiling — what the student is told. */
    @Enumerated(EnumType.STRING)
    @Column(name = "ai_level", length = 20)
    private SeniorityLevel aiLevel;

    /**
     * What the model said before the ceiling and the formula were applied.
     * Kept so the report can measure how often the two disagreed, which is a
     * genuine research artefact rather than a debugging leftover.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "ai_raw_level", length = 20)
    private SeniorityLevel aiRawLevel;

    @Column(name = "ai_rationale", columnDefinition = "TEXT")
    private String aiRationale;

    @Column(name = "ai_confidence", precision = 5, scale = 2)
    private BigDecimal aiConfidence;

    /** Share of required skills at APPLIED or above, verified or not. */
    @Column(name = "ratio_all", precision = 5, scale = 2)
    private BigDecimal ratioAll;

    /** Share backed by an objective source. Drives the JUNIOR ceiling. */
    @Column(name = "ratio_verified", precision = 5, scale = 2)
    private BigDecimal ratioVerified;

    /** Denominator: how many skills the target career requires. */
    @Column(name = "required_count")
    private Integer requiredCount;

    @Column(name = "model_used", length = 80)
    private String modelUsed;

    /** How many roadmap nodes this run marked as already covered. */
    @Column(name = "applied_node_count", nullable = false)
    @Builder.Default
    private Integer appliedNodeCount = 0;

    /** The auto-accepted recommendation, i.e. the audit trail for that change. */
    @Column(name = "recommendation_id")
    private UUID recommendationId;

    /** COMPLETED | FAILED. FAILED rows are runs that died before scoring. */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "COMPLETED";

    /** When scoring finished. Null on a row persisted before the AI call returned. */
    @Column(name = "computed_at")
    private LocalDateTime computedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
