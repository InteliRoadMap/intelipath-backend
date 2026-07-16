package com.inteliroadmap.backend.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Which alternative a student picked inside a CHOOSE_ONE group. The roadmap
 * template is shared across every student; this overlay records that, e.g.,
 * "for the Pick a Language group, this student chose Java" — so a Java student
 * is never forced to also complete C#. One row per (student, group).
 */
@Entity
@Table(
        name = "student_node_selections",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_student_node_selection", columnNames = {"user_id", "group_node_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class StudentNodeSelection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "selection_id")
    private UUID selectionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sns_student"))
    private Student student;

    // The CHOOSE_ONE parent node (the group the student is choosing within).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_node_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sns_group"))
    private SkillNode groupNode;

    // The ALTERNATIVE child the student picked within that group.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chosen_node_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sns_chosen"))
    private SkillNode chosenNode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
