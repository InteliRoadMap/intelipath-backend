package com.inteliroadmap.backend.domain.entity;

import com.inteliroadmap.backend.domain.enums.StudentSubjectSource;
import com.inteliroadmap.backend.domain.enums.StudentSubjectStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A student's declaration that they have taken an FPT subject. Drives the dynamic
 * roadmap: each PASSED subject seeds transcript evidence for the skills it covers.
 * One row per (student, subject).
 */
@Entity
@Table(
        name = "student_fpt_subjects",
        uniqueConstraints = @UniqueConstraint(name = "uq_sfs", columnNames = {"user_id", "subject_code"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentFptSubject {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "subject_code", length = 20, nullable = false)
    private String subjectCode;

    /** The curriculum this declaration was made under (for CURRICULUM_TERM inference). */
    @Column(name = "curriculum_id")
    private UUID curriculumId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private StudentSubjectStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 20, nullable = false)
    private StudentSubjectSource source;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void touch() {
        updatedAt = LocalDateTime.now();
    }
}
