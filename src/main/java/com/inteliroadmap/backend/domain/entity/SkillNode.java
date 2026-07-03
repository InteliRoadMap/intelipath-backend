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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "skill_nodes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SkillNode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "node_id")
    private UUID nodeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "career_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sn_career"))
    private CareerRole careerRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", foreignKey = @ForeignKey(name = "fk_sn_skill"))
    private Skill skill;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id", foreignKey = @ForeignKey(name = "fk_sn_type"))
    private NodeType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_node", foreignKey = @ForeignKey(name = "fk_sn_previous_node"))
    private SkillNode previousNode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_node", foreignKey = @ForeignKey(name = "fk_sn_parent_node"))
    private SkillNode parentNode;

    @Column(name = "node_name", nullable = false)
    private String nodeName;

    @Column(name = "node_level")
    private Integer nodeLevel;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "resource", columnDefinition = "jsonb")
    private com.fasterxml.jackson.databind.JsonNode resource;

    @Column(name = "completion_policy", length = 50)
    private String completionPolicy;

    @Column(name = "required_proficiency")
    private Integer requiredProficiency;

    @JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "evidence_keywords", columnDefinition = "jsonb")
    private com.fasterxml.jackson.databind.JsonNode evidenceKeywords;
}

