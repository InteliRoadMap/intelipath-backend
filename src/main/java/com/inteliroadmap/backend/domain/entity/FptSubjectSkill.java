package com.inteliroadmap.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * A catalog skill an FPT subject teaches (matched from the subject's CLOs). {@code skillId}
 * links to a real {@link Skill} when the name resolves; {@code skillName} is always kept so
 * the roadmap read-path can join by name even when no Skill row exists yet.
 */
@Entity
@Table(
        name = "fpt_subject_skills",
        uniqueConstraints = @UniqueConstraint(name = "uq_fss", columnNames = {"subject_code", "skill_name"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FptSubjectSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "subject_code", length = 20, nullable = false)
    private String subjectCode;

    @Column(name = "skill_id")
    private UUID skillId;

    @Column(name = "skill_name", nullable = false)
    private String skillName;
}
