package com.inteliroadmap.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "students")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Student {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "career_id", foreignKey = @ForeignKey(name = "fk_st_career"))
    private CareerRole careerRole;

    /** Free text, for display only. Whether FPT material is offered is decided by User.accountType. */
    @Column(name = "university_name")
    private String universityName;

    @Column(name = "admission_date")
    private LocalDate admissionDate;

    @Column(name = "major")
    private String major;

    @Column(name = "github_profile")
    private String githubProfile;

    @Column(name = "transcript_url", columnDefinition = "TEXT")
    private String transcriptUrl;

    @Column(name = "portfolio_slug", length = 100, unique = true)
    private String portfolioSlug;

    /** The FLM curriculum version this student follows (their cohort's program). */
    @Column(name = "fpt_curriculum_id")
    private UUID fptCurriculumId;

    /**
     * The specialisation combo the student picked within that curriculum (e.g.
     * {@code SE_COM10.2} for Intensive Java). Null until they choose: they then see the
     * curriculum's trunk subjects only, which is the honest answer — showing another
     * combo's subjects would be worse than showing none.
     */
    @Column(name = "fpt_combo_code", length = 40)
    private String fptComboCode;
}

