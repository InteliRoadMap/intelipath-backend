package com.inteliroadmap.backend.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "role_required_skills")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleRequiredSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "role_required_id")
    private UUID roleRequiredId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "career_id", nullable = false, foreignKey = @ForeignKey(name = "fk_rrs_career"))
    private CareerRole careerRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", nullable = false, foreignKey = @ForeignKey(name = "fk_rrs_skill"))
    private Skill skill;

    @Column(name = "importance_level")
    private String importanceLevel;
}
