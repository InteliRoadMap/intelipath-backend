package com.inteliroadmap.backend.domain.dto.response.roadmap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * One option of a CHOOSE_ONE group, with the two things a student weighs:
 * what they already bring to it, and what the market does with it.
 *
 * <p>Every number here is measured. {@code fitScore} comes from the same
 * {@code StackBranchScorer} pass that decides auto-selection, so the option
 * shown as the best fit is the one the system would have picked — the two
 * cannot disagree. {@code marketFrequency} is null rather than zero when no
 * posting data exists, because "we did not measure it" and "the market does not
 * want it" are different claims and a bar drawn at zero states the second one.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChoiceOptionResponse {

    private UUID nodeId;

    private String name;

    /**
     * Fit relative to the strongest option in this group, 0..1. Zero when the
     * student holds nothing that appears in the branch.
     */
    private Double fitScore;

    /** Why, in the student's own skills. Null when there is nothing to say. */
    private String fitReason;

    /** The student's skills that carried this branch, strongest first. */
    private List<MatchedSkillResponse> matchedSkills;

    /** Share of recent postings naming the option's skill, 0..1, or null. */
    private Double marketFrequency;

    /** Postings behind {@link #marketFrequency}. Travels with it. */
    private Integer marketJobCount;

    /**
     * The catalog skill {@link #marketJobCount} was counted over.
     *
     * <p>Sent so the count can be opened. "158 postings" is an aggregate, and an
     * aggregate the reader cannot expand into the rows behind it is a number
     * taken on trust — which is a poor basis for choosing a language. This is the
     * key the postings endpoint needs. Null when the node maps to no skill, in
     * which case there is no count to open either.
     */
    private UUID skillId;

    /** Descendants — what taking this option signs the student up for. */
    private Integer nodeCount;

    /** True for the option currently stored in {@code student_node_selections}. */
    private Boolean chosen;

    /** True when the system stored that selection rather than the student. */
    private Boolean autoSelected;

    /** One skill of the student's that counted towards this option. */

}
