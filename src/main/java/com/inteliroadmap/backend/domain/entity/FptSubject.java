package com.inteliroadmap.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One FPT subject (course) — cohort-independent syllabus facts. The subject code is
 * the natural primary key (e.g. "PRO192"). A subject's TERM is not stored here (it
 * varies per curriculum version); see {@link FptCurriculumSubject}. Skill coverage and
 * lesson resources live in {@link FptSubjectSkill} / {@link FptSubjectResource}.
 */
@Entity
@Table(name = "fpt_subjects")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FptSubject {

    @Id
    @Column(name = "code", length = 20)
    private String code;

    @Column(name = "name", nullable = false, columnDefinition = "TEXT")
    private String name;

    @Column(name = "credits")
    private Integer credits;

    @Column(name = "prerequisite", columnDefinition = "TEXT")
    private String prerequisite;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}
