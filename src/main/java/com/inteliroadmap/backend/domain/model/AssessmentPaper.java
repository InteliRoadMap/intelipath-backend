package com.inteliroadmap.backend.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * A whole graded paper for one family of careers.
 *
 * <p>{@code careerNames} rather than career ids: the ids are generated per
 * database, so a bank keyed on them could not survive a reseed and could not be
 * reviewed by reading it. Matching is case-insensitive on the career's name.
 *
 * @param version bumped whenever an item changes meaning. Stored alongside the
 *                served questions, so a result graded under an older bank can be
 *                identified rather than silently compared with newer ones.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AssessmentPaper(String scope,
                              List<String> careerNames,
                              int version,
                              List<AssessmentItem> items) {

    /** Total weight on offer, which is what a score is a share of. */
    public int totalWeight() {
        return items == null ? 0 : items.stream().mapToInt(AssessmentItem::weight).sum();
    }
}
