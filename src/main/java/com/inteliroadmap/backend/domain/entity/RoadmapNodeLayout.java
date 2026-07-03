package com.inteliroadmap.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Presentation-only placement of a roadmap node on the mentor's canvas.
 * Deliberately separate from {@link SkillNode}: nothing here participates in
 * unlock, progress, or skill-gap logic — that stays graph/prerequisite based.
 * One row per node.
 */
@Entity
@Table(name = "roadmap_node_layouts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoadmapNodeLayout {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "layout_id")
    private UUID layoutId;

    @Column(name = "node_id", nullable = false, unique = true)
    private UUID nodeId;

    @Column(name = "position_x")
    private Double positionX;

    @Column(name = "position_y")
    private Double positionY;

    @Column(name = "lane", length = 50)
    private String lane;

    @Column(name = "display_order")
    private Integer displayOrder;

    /** Bumped on each save; used for optimistic concurrency between mentors. */
    @Column(name = "layout_version", nullable = false)
    @Builder.Default
    private Integer layoutVersion = 1;

    @Column(name = "edited_by")
    private UUID editedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
