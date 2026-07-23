package com.inteliroadmap.backend.domain.entity;

import com.inteliroadmap.backend.domain.enums.EvidenceStatus;
import com.inteliroadmap.backend.domain.enums.EvidenceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "student_skill_evidence")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentSkillEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "evidence_id")
    private UUID evidenceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // Stays NULL until the student accepts a recommendation backed by this
    // evidence; only then is the student_skills row created and linked here.
    @Column(name = "student_skill_id")
    private UUID studentSkillId;

    @Column(name = "node_id")
    private UUID nodeId;

    @Column(name = "skill_name")
    private String skillName;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private EvidenceType sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(name = "evidence_text", columnDefinition = "TEXT")
    private String evidenceText;

    @Column(name = "confidence", precision = 5, scale = 2)
    private BigDecimal confidence;

    @Column(name = "detected_by", length = 50)
    private String detectedBy;

    @Column(name = "detected_at")
    private LocalDateTime detectedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EvidenceStatus status;
}
