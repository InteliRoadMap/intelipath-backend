package com.inteliroadmap.backend.domain.entity;

import com.inteliroadmap.backend.domain.dto.ai.SkillMatch;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The record of one AI analysis of one repository: what was read, what the model
 * was asked, and what it answered.
 *
 * <p>An import is not a read-only operation. It spends a model call and then
 * rewrites the student's profile — evidence rows, proficiency promotion, roadmap
 * completion. Until this existed the only trace of how that happened was a log
 * line inside the container, which meant an import that matched nothing was
 * indistinguishable from one that matched everything, and neither the student nor
 * anyone supporting them could look.
 *
 * <p>Written once, never updated. Re-importing the same repository replaces the
 * row (see the unique constraint) rather than appending, because two answers for
 * one repository with no way to tell which produced the profile on screen is
 * worse than one.
 *
 * <p>Note what is <em>absent</em>: the accepted/rejected fate of each matched
 * skill. That belongs to {@link StudentSkillEvidence} and keeps moving after the
 * import — the promoter can supersede a row hours later. Snapshotting it here
 * would produce an audit screen that confidently disagrees with the profile it is
 * explaining, so the read path joins the live evidence instead.
 */
@Entity
@Table(name = "github_import_audit")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GithubImportAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "audit_id")
    private UUID auditId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** The URL the student imported — also the join key to the evidence rows it produced. */
    @Column(name = "repo_url", columnDefinition = "TEXT", nullable = false)
    private String repoUrl;

    @Column(name = "repo_full_name", columnDefinition = "TEXT")
    private String repoFullName;

    @Column(name = "analyzed_at", nullable = false)
    private LocalDateTime analyzedAt;

    /** Which model answered. A different model is a different answer. */
    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "fetch_mode", length = 20)
    private String fetchMode;

    /** Size of the catalog put into the prompt. 1466 skills and 200 skills are different questions. */
    @Column(name = "catalog_size")
    private Integer catalogSize;

    @Column(name = "career_name", columnDefinition = "TEXT")
    private String careerName;

    /** Every file the importer attempted, including the ones that came back empty. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sources", columnDefinition = "jsonb")
    private List<SourceRead> sources;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tech_stack", columnDefinition = "jsonb")
    private Map<String, Object> techStack;

    /** The model's matches as returned, before the evidence layer clamped or dropped any. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "matched_skills", columnDefinition = "jsonb")
    private List<SkillMatch> matchedSkills;

    /**
     * CONTRIBUTED, NOT_CONTRIBUTED or UNKNOWN.
     *
     * <p>Three values because a student who wrote nothing and a repository we could not
     * get an answer about are different situations, and only the first is theirs.
     */
    @Column(name = "authorship_verdict", length = 20)
    private String authorshipVerdict;

    @Column(name = "author_login", columnDefinition = "TEXT")
    private String authorLogin;

    @Column(name = "author_commits")
    private Integer authorCommits;

    @Column(name = "total_commits")
    private Integer totalCommits;

    /** Shown to the student verbatim — a verdict without a reason is an accusation without a charge. */
    @Column(name = "authorship_reason", columnDefinition = "TEXT")
    private String authorshipReason;

    /** GitHub's own measurement of the committed code, language → bytes. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "language_bytes", columnDefinition = "jsonb")
    private Map<String, Long> languageBytes;

    /** This student's own commit subjects: what they did, not what the repository is. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "commit_subjects", columnDefinition = "jsonb")
    private List<String> commitSubjects;

    /** True when the verdict withheld skill evidence, so the screen can say why nothing changed. */
    @Column(name = "evidence_blocked")
    private Boolean evidenceBlocked;

    /**
     * One file the importer tried to read.
     *
     * @param path  the repository path attempted, e.g. {@code README.md}
     * @param chars how much of it reached the prompt, after truncation
     * @param found whether anything came back at all — a missing README and an empty
     *              one produce the same prompt, and the difference is worth showing
     */
    public record SourceRead(String path, int chars, boolean found) {}
}
