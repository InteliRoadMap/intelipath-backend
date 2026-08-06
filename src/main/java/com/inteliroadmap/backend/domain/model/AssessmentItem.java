package com.inteliroadmap.backend.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.inteliroadmap.backend.domain.enums.AssessmentItemKind;

import java.util.List;
import java.util.UUID;

/**
 * One question in a graded assessment paper, as loaded from the question bank.
 *
 * <p><b>Authored, not generated.</b> The bank lives in {@code resources/assessment}
 * and is reviewed like code. The three reasons the old self-report builder gave for
 * not generating questions all still hold, and one more has been added by giving
 * questions right answers: a generated question has no trustworthy key, so a
 * student could be marked wrong by a hallucination and have no way to appeal.
 *
 * <p><b>{@code skills} is what makes a score mean something.</b> Getting the
 * caching question right is evidence about caching, so each item names the catalog
 * skills it probes; {@code SkillNameCanonicalizer} resolves those names to real
 * rows when the paper is loaded, and {@link #skillIds} carries the result. An item
 * naming a skill that is not in the catalog resolves to nothing and simply
 * contributes no evidence — it still scores.
 *
 * @param tier        1, 2 or 3, matching {@code RoadmapTierResolver}'s bands. What a
 *                    student gets right at each tier is the whole level signal, so
 *                    this is not decoration: eight tier-1 answers and no tier-3 is a
 *                    different person from four of each.
 * @param answer      the correct option key(s). Empty for the rubric-graded kinds —
 *                    never sent to the client for any kind.
 * @param rubric      what the model is told to look for. Empty for auto-graded kinds.
 * @param explanation shown after submission, right or wrong. An assessment that
 *                    tells a student their level and not what they missed is a
 *                    grade, not a teaching moment.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AssessmentItem(String id,
                             AssessmentItemKind kind,
                             int tier,
                             String topic,
                             List<String> skills,
                             String prompt,
                             List<AssessmentChoice> choices,
                             List<String> answer,
                             String language,
                             String starterCode,
                             List<RubricCriterion> rubric,
                             String explanation,
                             List<UUID> skillIds) {

    /** Points an item is worth: its tier. A tier-3 answer says more than a tier-1 one. */
    public int weight() {
        return Math.max(1, tier);
    }

    /** The same item with resolved catalog ids, produced by the loader. */
    public AssessmentItem withSkillIds(List<UUID> resolved) {
        return new AssessmentItem(id, kind, tier, topic, skills, prompt, choices, answer,
                language, starterCode, rubric, explanation, resolved);
    }
}
