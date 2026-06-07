package com.inteliroadmap.backend.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "portfolio_project")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioProject {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "project_id")
    private UUID projectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_pp_user"))
    private User user;

    @Column(name = "repo_id")
    private Long repoId;

    @Column(name = "repo_url", columnDefinition = "TEXT")
    private String repoUrl;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "stars")
    private Integer stars = 0;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "tech_stack", columnDefinition = "jsonb")
    private Object techStack;
}
