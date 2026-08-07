package com.inteliroadmap.backend.domain.dto.response.roadmap;

import com.inteliroadmap.backend.domain.dto.response.market.SkillDemandResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * One skill the target career is graded on, whether or not the student has it.
 *
 * <p>This is the denominator behind the readiness percentage and the level badge,
 * sent as rows rather than a count so the skill map can draw it. Skills the
 * student lacks are rows too, carrying {@code proficiency = null} — an absent
 * bubble says nothing, an empty one says "this is the gap".
 *
 * <p>Not derived from the node list: nodes and skills are not the same thing
 * (several nodes can teach one skill, and a core skill can have no node at all),
 * and building a second view of the same question from a different source is
 * exactly how the dashboard came to show "5/1466" beside "0 of 29".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoreSkillResponse {

    private UUID skillId;
    private String skillName;

    /** HIGH — the map sizes bubbles by it, and today the core set is HIGH only. */
    private String importance;

    /** 1..4, or null when the student does not hold it at a level that counts. */
    private Short proficiency;

    /** GITHUB | TRANSCRIPT | MENTOR, null for a self-report. */
    private String verifiedBy;

    /**
     * Market pull for this skill in this career. Null when no posting in the
     * window mentions it — which the map must draw as "unknown", not as zero.
     */
    private SkillDemandResponse marketDemand;
}
