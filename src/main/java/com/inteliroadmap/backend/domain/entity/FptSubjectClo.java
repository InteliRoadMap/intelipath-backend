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

import java.util.UUID;

/**
 * One Course Learning Outcome of an FPT subject, as published in its syllabus
 * ("CLO3 — be able to work with JDBC").
 *
 * These are the syllabus's own words about what the course teaches, so they are what a
 * subject page shows a student. The scraper also feeds them to the skill matcher, but
 * that mapping is lossy and lives upstream — these rows stay verbatim.
 */
@Entity
@Table(name = "fpt_subject_clos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FptSubjectClo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "subject_code", length = 20, nullable = false)
    private String subjectCode;

    /** The syllabus's own label, e.g. {@code CLO1}. Unique per subject. */
    @Column(name = "code", length = 20, nullable = false)
    private String code;

    @Column(name = "outcome", nullable = false, columnDefinition = "TEXT")
    private String outcome;

    /** Syllabus order; CLO codes sort like strings ("CLO10" before "CLO2"). */
    @Column(name = "order_index", nullable = false)
    private int orderIndex;
}
