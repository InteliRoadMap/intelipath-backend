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
public class SkillNode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "node_id")
    private UUID nodeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "career_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sn_career"))
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private CareerRole careerRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prerequisite", foreignKey = @ForeignKey(name = "fk_sn_prerequisite"))
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.SET_NULL)
    private SkillNode prerequisite;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "level")
    private Integer level;

    @Column(name = "order_index")
    private Integer orderIndex;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "resource", columnDefinition = "jsonb")
    private String resource;
}
