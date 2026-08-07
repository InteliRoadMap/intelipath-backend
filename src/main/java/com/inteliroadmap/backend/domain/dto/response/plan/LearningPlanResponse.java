package com.inteliroadmap.backend.domain.dto.response.plan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * What this student should do next, and why.
 *
 * <p>This is the inverse of the roadmap endpoint. That one starts from the
 * career's node catalog and filters it down; useful, but it can only ever answer
 * "what exists". This starts from the student — the skills they can prove, the
 * level they assessed at — and from the market, and derives the work. The node
 * catalog is consulted last, as the source of something to actually read.
 *
 * <p>So a step exists because a skill is missing and wanted, not because a node
 * happened to be in the table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningPlanResponse {

    private String targetCareerRole;

    /** FRESHER | JUNIOR | MID | SENIOR, or null when they skipped the assessment. */
    private String level;

    /** One sentence the student can read before any of the detail. */
    private String summary;

    /** Required skills of the target role, and how many are already covered. */
    private Integer requiredSkillCount;
    private Integer coveredSkillCount;

    /** The ranked work, most valuable first. */
    private List<PlanStepResponse> steps;

    /**
     * Skills the plan deliberately skips, with the evidence that earned the skip.
     *
     * <p>Sent because "we left this out" is a claim about the student, and a
     * claim about someone should come with its reason attached.
     */
    private List<PlanSkipResponse> alreadyCovered;
}
