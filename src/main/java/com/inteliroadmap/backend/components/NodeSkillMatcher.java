package com.inteliroadmap.backend.components;

import com.fasterxml.jackson.databind.JsonNode;
import com.inteliroadmap.backend.domain.entity.SkillNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Decides whether a roadmap node refers to something the student already holds,
 * for the 671 of 1.200 nodes that carry no {@code skill_id} at all.
 *
 * <p>Those nodes cannot be joined to {@code student_skills} by id, so without a
 * fallback every Data Science roadmap (446 of 461 nodes skill-less) would be
 * personalised into exactly the graph it is today. The fallback is deliberately
 * conservative: it answers "does the student hold this" and nothing else, so a
 * wrong guess only reorders a node, never marks it complete.
 *
 * <p>The same matching already exists, private, inside
 * {@code RoadmapPersonalizationServiceImpl} ({@code evidenceKeywords},
 * {@code fuzzyMatchNodes}). This is a shared copy rather than a refactor of it —
 * personalization writes progress rows, and changing its matcher to serve an
 * ordering concern would put that at risk for no gain. Worth merging once both
 * sides have tests.
 */
@Component
public class NodeSkillMatcher {

    /**
     * Minimum length either side of a fuzzy match must have. Below this,
     * generic fragments like "go" or "r" match nearly every node name.
     */
    private static final int MIN_FUZZY_MATCH_LENGTH = 3;

    /**
     * Whether {@code heldSkillNamesLower} contains something this node is about.
     *
     * <p>Tried in order of trustworthiness: the node's own skill name, then its
     * declared {@code evidence_keywords} (matched whole — the list holds generic
     * words like "types" that would swallow anything as a substring), then the
     * node name as a fuzzy containment either way ("React.js" vs "React").
     */
    public boolean isHeld(SkillNode node, Set<String> heldSkillNamesLower) {
        if (node == null || heldSkillNamesLower == null || heldSkillNamesLower.isEmpty()) {
            return false;
        }
        if (node.getSkill() != null && node.getSkill().getSkillName() != null
                && heldSkillNamesLower.contains(node.getSkill().getSkillName().trim().toLowerCase())) {
            return true;
        }
        for (String keyword : evidenceKeywords(node)) {
            if (heldSkillNamesLower.contains(keyword)) {
                return true;
            }
        }
        return fuzzyMatches(node.getNodeName(), heldSkillNamesLower);
    }

    /** Containment in either direction, so a broader and a narrower name both hit. */
    private boolean fuzzyMatches(String nodeName, Set<String> heldSkillNamesLower) {
        if (nodeName == null) {
            return false;
        }
        String candidate = nodeName.trim().toLowerCase();
        if (candidate.length() < MIN_FUZZY_MATCH_LENGTH) {
            return false;
        }
        for (String held : heldSkillNamesLower) {
            if (held.length() < MIN_FUZZY_MATCH_LENGTH) {
                continue;
            }
            if (candidate.contains(held) || held.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    /** The node's declared evidence synonyms, lowercased; empty when absent or malformed. */
    public List<String> evidenceKeywords(SkillNode node) {
        JsonNode keywords = node.getEvidenceKeywords();
        if (keywords == null || !keywords.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode keyword : keywords) {
            String value = keyword.asText("").trim().toLowerCase();
            if (!value.isEmpty()) {
                result.add(value);
            }
        }
        return result;
    }
}
