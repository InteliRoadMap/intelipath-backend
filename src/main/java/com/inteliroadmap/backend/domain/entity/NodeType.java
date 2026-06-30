package com.inteliroadmap.backend.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.inteliroadmap.backend.domain.enums.StageType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;

import java.util.UUID;

@Entity
@Table(name = "node_types")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class NodeType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "type_id")
    private UUID typeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false)
    private StageType stage;

    @Column(name = "unlock_key_required")
    private Boolean unlockKeyRequired;

    @JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "stage_unlock_key", columnDefinition = "jsonb")
    private Object stageUnlockKey;

    @Column(name = "weight")
    private Integer weight;
}
