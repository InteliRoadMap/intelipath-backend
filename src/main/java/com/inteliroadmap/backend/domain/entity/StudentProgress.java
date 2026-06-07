package com.inteliroadmap.backend.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "student_progress")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "progress_id")
    private UUID progressId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sp_student"))
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sp_node"))
    private SkillNode skillNode;

    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "IN_PROGRESS";

    @Column(name = "create_at", nullable = false)
    private LocalDateTime createAt;

    @Column(name = "complete_at")
    private LocalDateTime completeAt;

    @PrePersist
    public void prePersist() {
        if (createAt == null) {
            createAt = LocalDateTime.now();
        }
    }
}
