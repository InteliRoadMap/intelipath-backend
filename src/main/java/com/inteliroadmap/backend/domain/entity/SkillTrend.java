package com.inteliroadmap.backend.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "skill_trends")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkillTrend {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "trend_id")
    private UUID trendId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", nullable = false, foreignKey = @ForeignKey(name = "fk_strd_skill"))
    private Skill skill;
//    @Column(name = "skill_id", nullable = false)
//    private UUID skillId;

    @Column(name = "jobs_needed")
    private Integer jobsNeeded;

    @Column(name = "week_stamp")
    private LocalDate weekStamp;
}

