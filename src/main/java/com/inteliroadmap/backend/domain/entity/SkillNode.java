package com.inteliroadmap.backend.domain.entity;

import jakarta.persistence.*;
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
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SkillNode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "node_id")
    private UUID nodeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "career_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sn_career"))
    private CareerRole careerRole;
//    @Column(name = "career_id", nullable = false)
//    private UUID careerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connect_to", foreignKey = @ForeignKey(name = "fk_sn_connect_to"))
    private SkillNode connectTo;
//    @Column(name = "previous_node")
//    private UUID previousNode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_node_of", foreignKey = @ForeignKey(name = "fk_sn_child_node_of"))
    private SkillNode childNodeOf;
//    @Column(name = "parent_node")
//    private UUID parentNode;

    @Column(name = "node_name", nullable = false)
    private String nodeName;

    @Column(name = "node_level")
    private Integer nodeLevel;

    @Column(name = "skill_id")
    private UUID skillId;

    @Column(name = "type_id")
    private UUID typeId;

    @JdbcTypeCode(org.hibernate.type.SqlTypes.ARRAY)
    @Column(name = "prerequisite", columnDefinition = "uuid[]")
    private List<UUID> prerequisite;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "resource", columnDefinition = "jsonb")
    private Object resource;
}

