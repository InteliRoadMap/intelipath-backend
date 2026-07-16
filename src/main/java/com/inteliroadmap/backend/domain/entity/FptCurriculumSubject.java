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

    /** Composite primary key for {@link FptCurriculumSubject}. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PK implements java.io.Serializable {
        private UUID curriculumId;
        private String subjectCode;
    }
}
