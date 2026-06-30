package com.inteliroadmap.backend.domain.entity;

import com.inteliroadmap.backend.domain.enums.RoadmapStepStatus;
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
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sp_student"))
    private Student student;
//    @Column(name = "user_id", nullable = false)
//    private UUID studentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sp_node"))
    private SkillNode skillNode;
//    @Column(name = "node_id", nullable = false)
//    private UUID nodeId;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RoadmapStepStatus status = RoadmapStepStatus.IN_PROGRESS;

    @Column(name = "create_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "complete_at")
    private LocalDateTime completedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

