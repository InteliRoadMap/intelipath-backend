package com.inteliroadmap.backend.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.inteliroadmap.backend.domain.enums.StageType;
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

import java.util.List;
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

    // Typed List<String> (not Object) so Hibernate's JSON mapper can bind it;
    // an untyped Object here fails at flush with ArrayList->String ClassCastException.
    @JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "stage_unlock_key", columnDefinition = "jsonb")
    private List<String> stageUnlockKey;

    @Column(name = "weight")
    private Integer weight;
}
