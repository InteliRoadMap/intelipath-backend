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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "university_id", foreignKey = @ForeignKey(name = "fk_st_university"))
    private University university;

    @Column(name = "university_name")
    private String universityName;

    @Column(name = "year_of_admission")
    private Integer yearOfAdmission;

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
}

