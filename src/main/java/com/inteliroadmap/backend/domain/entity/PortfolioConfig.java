package com.inteliroadmap.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "portfolio_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "config_id")
    private UUID configId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true, foreignKey = @ForeignKey(name = "fk_pc_user"))
    private Student user;

    @Column(name = "theme", length = 50)
    @Builder.Default
    private String theme = "dark";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "theme_colors", columnDefinition = "jsonb")
    private Map<String, Object> themeColors;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fonts", columnDefinition = "jsonb")
    private Map<String, Object> fonts;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hero_section", columnDefinition = "jsonb")
    private Map<String, Object> heroSection;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skills_section", columnDefinition = "jsonb")
    private Map<String, Object> skillsSection;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

