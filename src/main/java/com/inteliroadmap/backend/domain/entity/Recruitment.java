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
import java.util.UUID;

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

    /**
     * company + title + location, slugified by the scraper.
     *
     * <p>Two rows sharing this are the same job advertised twice, so statistics
     * count the key once. Null on rows scraped before this existed; those are
     * counted individually, which is the old behaviour rather than a silent drop.
     */
    @Column(name = "dedup_key", length = 300)
    private String dedupKey;

    /**
     * Which career this posting is for, derived from its title by keyword.
     *
     * <p>Null when the title names no specialisation — "Software Engineer", "IT Staff".
     * That is an answer, not a gap: a posting filed under the wrong career becomes
     * evidence that the career requires whatever the posting happened to mention, which
     * is worse than one counted for no career at all. Roughly 45% of current postings
     * stay null and are excluded from per-career demand.
     */
    @Column(name = "career_id")
    private UUID careerId;

    /**
     * FRESHER | JUNIOR | MID | SENIOR | UNKNOWN, derived from the title and the
     * stated experience.
     *
     * <p>UNKNOWN is a real value, not a placeholder for "not yet processed": some
     * postings simply do not say. Those stay in every result — a job we could not
     * label is still a job, and dropping them would quietly shrink the market.
     */
    @Column(name = "seniority", length = 20)
    private String seniority;

    @Column(name = "classified_at")
    private java.time.LocalDateTime classifiedAt;
}
