package com.inteliroadmap.backend.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "student_skills")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "student_skill_id")
    private UUID studentSkillId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ss_student"))
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ss_skill"))
    private Skill skill;

    @Column(name = "custom_description", columnDefinition = "TEXT")
    private String customDescription;

    @Column(name = "tech_stack")
    private String techStack;

    // ── Level (additive; all nullable so existing rows stay valid) ──────────
    /**
     * How well the student knows this skill: 1 AWARE, 2 PRACTICED, 3 APPLIED,
     * 4 PROFESSIONAL. Null on rows written before the self-assessment existed,
     * and on rows the roadmap auto-synced from completed nodes — absent means
     * "we have never asked", which is not the same as "level 1".
     */
    @Column(name = "proficiency")
    private Short proficiency;

    /** False once an objective source (transcript, repo, mentor) set the level. */
    @Column(name = "self_declared", nullable = false)
    @Builder.Default
    private Boolean selfDeclared = true;

    /**
     * TRANSCRIPT | GITHUB | MENTOR, or null for self-declaration.
     *
     * <p>Objective sources outrank self-declaration, so this is what stops a
     * re-taken assessment from overwriting a level that was actually verified.
     * It is also the numerator of the "verified ratio" behind the JUNIOR ceiling.
     */
    @Column(name = "verified_by", length = 30)
    private String verifiedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
