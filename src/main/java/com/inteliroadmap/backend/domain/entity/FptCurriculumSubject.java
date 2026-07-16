package com.inteliroadmap.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Places one subject into one curriculum at a given term. The same subject code appears
 * in many curricula at different semesters — this join row is where that difference is
 * stored, keeping {@link FptSubject} deduplicated.
 *
 * A curriculum is not a flat list: it reserves combo slots whose real subjects depend on
 * the specialisation the student picked (Intensive Java teaches HSF302/SBA301/MSS301,
 * .NET teaches others). {@link #comboCode} carries that split.
 */
@Entity
@Table(name = "fpt_curriculum_subjects")
@IdClass(FptCurriculumSubject.PK.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FptCurriculumSubject {

    @Id
    @Column(name = "curriculum_id")
    private UUID curriculumId;

    @Id
    @Column(name = "subject_code", length = 20)
    private String subjectCode;

    @Column(name = "semester")
    private Integer semester;

    /**
     * The specialisation combo this subject belongs to (e.g. {@code SE_COM10.2}), or null
     * for a trunk subject every student on the curriculum takes. Not part of the key: a
     * subject sits in at most one combo per curriculum.
     */
    @Column(name = "combo_code", length = 40)
    private String comboCode;

    /** Display label for {@link #comboCode}, denormalised so listing combos needs no join. */
    @Column(name = "combo_name", columnDefinition = "TEXT")
    private String comboName;

    /** Composite primary key for {@link FptCurriculumSubject}. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PK implements java.io.Serializable {
        private UUID curriculumId;
        private String subjectCode;
    }
}
