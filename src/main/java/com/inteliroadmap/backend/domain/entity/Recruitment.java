package com.inteliroadmap.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.Map;

/**
 * AI-processed recruitment, mirroring the service's processed_recruitments output.
 *   recruitment_infos = { link, title, salary, location, experience }
 *   descriptions      = { tags, descriptions, general_infos, related_tags } (AI-summarised)
 */
@Entity
@Table(name = "recruitments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recruitment {

    @Id
    @Column(name = "recruitment_id", nullable = false)
    private String topCvRecruitmentId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recruitment_infos", columnDefinition = "jsonb")
    private Map<String, Object> recruitmentInfos;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "descriptions", columnDefinition = "jsonb")
    private Map<String, Object> descriptions;

    @Column(name = "posted_date")
    private LocalDate postedDate;

    @Column(name = "application_deadline")
    private LocalDate applicationDeadline;
}
