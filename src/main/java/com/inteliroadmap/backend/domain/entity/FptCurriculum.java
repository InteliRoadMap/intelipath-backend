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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One FLM curriculum version, per cohort and program (e.g. "BIT_SE_K21B"). Multiple
 * versions coexist; syncing one never overwrites another. Which subjects it contains
 * and at which term lives in {@link FptCurriculumSubject}.
 */
@Entity
@Table(name = "fpt_curricula")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FptCurriculum {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    /** FLM curriculum code, e.g. "BIT_SE_K21B". Unique. */
    @Column(name = "code", nullable = false, length = 60, unique = true)
    private String code;

    /** FLM numeric curriculum id used to scrape it. */
    @Column(name = "curid", length = 20)
    private String curid;

    /** Program slug parsed from the code, e.g. "SE". */
    @Column(name = "program", length = 20)
    private String program;

    /** Cohort (K) number parsed from the code, e.g. 21. */
    @Column(name = "cohort")
    private Integer cohort;

    /** Intake batch parsed from the code, e.g. "B" or "D_K20A". */
    @Column(name = "batch", length = 20)
    private String batch;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    /** Fallback curriculum for students whose cohort has no matching version. */
    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;
}
