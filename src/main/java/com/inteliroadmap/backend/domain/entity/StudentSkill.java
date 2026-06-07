package com.inteliroadmap.backend.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "student_skills")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "student_skill_id")
    private UUID studentSkillId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ss_student"))
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ss_skill"))
    private Skill skill;
}
