package com.inteliroadmap.backend.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "skill_nodes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SkillNode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "node_id")
    private UUID nodeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "career_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sn_career"))
    private CareerRole careerRole;

    @Column(name = "subtree_name")
    private String subtreeName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connect_to", foreignKey = @ForeignKey(name = "fk_sn_connect_to"))
    private SkillNode connectTo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_node_of", foreignKey = @ForeignKey(name = "fk_sn_child_node_of"))
    private SkillNode childNodeOf;

    @Column(name = "node_name", nullable = false)
    private String nodeName;

    @Column(name = "level")
    private Integer level;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "resource", columnDefinition = "jsonb")
    private Object resource;
}
