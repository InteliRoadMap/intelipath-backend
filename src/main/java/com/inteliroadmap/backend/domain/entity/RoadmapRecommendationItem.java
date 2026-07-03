package com.inteliroadmap.backend.domain.entity;

import com.inteliroadmap.backend.domain.enums.RecommendationAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "roadmap_recommendation_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoadmapRecommendationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "rec_item_id")
    private UUID recItemId;

    @Column(name = "recommendation_id", nullable = false)
    private UUID recommendationId;

    @Column(name = "node_id", nullable = false)
    private UUID nodeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private RecommendationAction action;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @JdbcTypeCode(org.hibernate.type.SqlTypes.ARRAY)
    @Column(name = "evidence_ids", columnDefinition = "uuid[]")
    private List<UUID> evidenceIds;

    @Column(name = "confidence", precision = 5, scale = 2, nullable = false)
    private BigDecimal confidence;
}
