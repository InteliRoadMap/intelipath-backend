package com.inteliroadmap.backend.domain.dto.response.portfolio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * The account of one AI analysis: what was read, what the model was asked, what it
 * answered, and what the profile did with each answer.
 *
 * <p>Two different kinds of fact are combined here on purpose. Everything down to
 * {@code matchedSkills} is a <em>snapshot</em> — it is what happened during the run
 * and cannot change afterwards. The {@code status} on each skill is read <em>live</em>
 * from the evidence table at the moment this is requested, because the promoter keeps
 * moving those rows long after the import finished. A frozen copy would drift into
 * disagreeing with the profile the student is looking at.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GithubImportAuditResponse {

    private String repoUrl;
    private String repoFullName;
    /** ISO-8601 instant of the analysis. */
    private String analyzedAt;

    /** Which model answered. */
    private String model;
    /**
     * AUTHENTICATED — read with the student's own token, so private repositories were
     * visible. ANONYMOUS — read from the public raw host, where a private repository
     * returns nothing at all and the model sees an empty project.
     */
    private String fetchMode;

    /** How many career skills went into the prompt, and whose career they were. */
    private int catalogSize;
    private String careerName;

    /** Every file the importer tried, in the order it tried them. */
    private List<SourceReadResponse> sources;

    /**
     * CONTRIBUTED, NOT_CONTRIBUTED or UNKNOWN — whether GitHub credits this student with
     * commits here. Null for imports analysed before authorship was checked at all.
     */
    private String authorshipVerdict;
    private String authorLogin;
    private int authorCommits;
    private int totalCommits;
    /** Plain-language explanation, shown as-is. A verdict without a reason is an accusation. */
    private String authorshipReason;
    /** True when the verdict withheld skill evidence, so the screen can say why nothing changed. */
    private boolean evidenceBlocked;

    /** GitHub's own byte count per language — what was written, not what was declared. */
    private Map<String, Long> languageBytes;
    /** The student's own commit subjects, newest first. */
    private List<String> commitSubjects;

    private String summary;
    private Map<String, Object> techStack;

    /** The model's matches, each carrying what the profile has since done with it. */
    private List<MatchedSkillResponse> skills;

}
