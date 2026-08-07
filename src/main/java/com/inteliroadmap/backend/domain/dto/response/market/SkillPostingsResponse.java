package com.inteliroadmap.backend.domain.dto.response.market;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * The postings behind a market number.
 *
 * <p>Every market figure on the roadmap is an aggregate — "158 postings", "19% of
 * jobs ask for this" — and the student is asked to pick a career on the strength
 * of them. A number nobody can open is a number taken on faith. This is what the
 * count is made of, so the claim can be checked against the source instead of
 * against us.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillPostingsResponse {

    private String skillName;

    /** Every posting that mentions the skill, not just the ones returned below. */
    private long totalCount;

    /**
     * The sample, newest first.
     *
     * <p>Capped, because a student checking a claim reads the first few and the
     * rest is weight. {@link #totalCount} is what stops the cap from being a lie:
     * a list of 30 under a headline of 158 must say so.
     */
    @Builder.Default
    private List<PostingResponse> postings = List.of();

}
