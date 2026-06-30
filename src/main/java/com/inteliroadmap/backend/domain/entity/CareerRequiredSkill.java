package com.inteliroadmap.backend.domain.entity;

import com.inteliroadmap.backend.domain.enums.ImportanceLevel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "career_required_skills")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CareerRequiredSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "skill_required_id")
    private UUID skillRequiredId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "career_id", nullable = false, foreignKey = @ForeignKey(name = "fk_crs_career"))
    private CareerRole careerRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", nullable = false, foreignKey = @ForeignKey(name = "fk_crs_skill"))
    private Skill skill;

    @Column(name = "importance_level")
    @Enumerated(EnumType.STRING)
    private ImportanceLevel importanceLevel;
}

