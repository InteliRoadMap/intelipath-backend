package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.domain.entity.SkillNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Carries proven ability down into the sub-skills it already covers.
 *
 * <h2>The problem</h2>
 *
 * <p>Evidence reaches a node by <em>naming</em> it: an imported repository proves
 * {@code Java}, and {@code RoadmapPersonalizationServiceImpl} matches that to the
 * node whose catalog skill is Java. It stops there. Java's own roadmap holds 71
 * more nodes — {@code Exception Handling}, {@code Collections}, {@code Optionals}
 * — each carrying its own catalog skill, and the student has evidence for none of
 * those names. So a student the system has already graded as a Java developer is
 * handed 71 checkboxes and told to click them one at a time.
 *
 * <p>That is not a small annoyance. It is the system disagreeing with itself: the
 * header says FRESHER on the strength of Java, and the Java roadmap says you have
 * done none of it.
 *
 * <h2>The rule</h2>
 *
 * <p>Proof of a skill is proof of its sub-skills <b>strictly below</b> the tier
 * the student's level unlocks.
 *
 * <p>Strictly below, not at-or-below, and that is the whole safety of this class.
 * {@link RoadmapTierResolver#ceilingFor} is deliberately generous — it hands a
 * student one tier past their level so they can see where the road goes — so
 * completing everything up to the ceiling would complete everything they can see.
 * Measured on Java: the subtree is entirely tier 1 and 2, so at FRESHER
 * (ceiling 2) at-or-below would tick all 72 nodes and the roadmap would be over
 * before it began. Strictly-below ticks the 9 tier-1 nodes and leaves the 47
 * tier-2 ones to be learned.
 *
 * <h2>What this deliberately does not decide</h2>
 *
 * <p><b>It does not complete anything.</b> It returns candidates. Every existing
 * gate still runs afterwards in {@code offerCandidate}: a node must allow
 * evidence at all ({@code MANUAL_ONLY} and {@code NEVER_COMPLETE} are refused
 * there, which is 16 of Java's 25 tier-1 nodes), the discounted confidence must
 * clear that node's own bar, and it must clear the importance floor of the
 * skill's grade for the career. A HIGH-importance sub-skill needs 0.85, which
 * inherited evidence cannot reach — so this can never quietly tick a core career
 * skill.
 *
 * <p><b>It does not invent a confidence.</b> {@link #DESCENDANT_DISCOUNT} scales
 * the parent's, because knowing Java is real evidence about Optionals and weaker
 * evidence than a repository that uses Optionals by name. The numbers that fall
 * out are the useful ones: a GitHub-verified skill (0.85–0.90) reaches 0.72–0.765
 * and clears a 0.55 bar; an APPLIED self-declaration (0.70) reaches 0.595 and
 * still clears it; a PRACTICED one (0.55) reaches 0.47 and does not. Having
 * practised something is not grounds to skip learning it — the same line
 * {@code PROFICIENCY_CONFIDENCE} already draws, extended one level down.
 */
@Component
@RequiredArgsConstructor
public class AbilityFloorPropagator {

    /**
     * How much weaker inherited evidence is than the evidence it came from.
     *
     * <p>One factor rather than a curve by depth: a tier-1 node three levels down
     * inside Java is not less basic than a tier-1 node one level down, and tier is
     * already the axis this class reasons on. A second decay would be a number
     * with no meaning behind it.
     */
    public static final BigDecimal DESCENDANT_DISCOUNT = new BigDecimal("0.85");

    private final RoadmapTierResolver roadmapTierResolver;

    /** What inherited evidence is worth, given what the direct evidence was worth. */
    public BigDecimal inheritedConfidence(BigDecimal directConfidence) {
        return directConfidence == null ? null : directConfidence.multiply(DESCENDANT_DISCOUNT);
    }

    /**
     * The sub-skills a proven node already covers.
     *
     * @param provenNodeIds nodes the student's evidence matched directly
     * @param careerNodes   every node on the career, used to walk parent links
     * @param level         the student's seniority band; null means "no claim",
     *                      and no claim propagates nothing — see below
     * @return descendants strictly below the level's tier ceiling, excluding the
     *         proven nodes themselves; never null
     */
    public List<SkillNode> coveredDescendants(Set<UUID> provenNodeIds, List<SkillNode> careerNodes, String level) {
        if (provenNodeIds == null || provenNodeIds.isEmpty() || careerNodes == null || careerNodes.isEmpty()) {
            return List.of();
        }
        // An unassessed student propagates nothing. RoadmapTierResolver answers
        // "no level" with the most generous ceiling, which is right for *showing*
        // a roadmap and wrong for *completing* one: it would hand the largest
        // auto-completion to the student we know the least about.
        if (level == null || level.isBlank()) {
            return List.of();
        }
        short ceiling = roadmapTierResolver.ceilingFor(level);

        Map<UUID, List<SkillNode>> childrenOf = new HashMap<>();
        for (SkillNode node : careerNodes) {
            SkillNode parent = node.getParentNode();
            if (parent != null && parent.getNodeId() != null) {
                childrenOf.computeIfAbsent(parent.getNodeId(), key -> new ArrayList<>()).add(node);
            }
        }

        List<SkillNode> covered = new ArrayList<>();
        Set<UUID> visited = new HashSet<>(provenNodeIds);
        Deque<UUID> queue = new ArrayDeque<>(provenNodeIds);
        while (!queue.isEmpty()) {
            for (SkillNode child : childrenOf.getOrDefault(queue.poll(), List.of())) {
                if (child.getNodeId() == null || !visited.add(child.getNodeId())) {
                    continue;
                }
                // Walk past a node that is too advanced rather than stopping at it.
                // A tier-2 heading can own tier-1 children — "More about OOP" sits
                // over the basics — and cutting the walk there would strand them.
                if (isBelowCeiling(child, ceiling)) {
                    covered.add(child);
                }
                queue.add(child.getNodeId());
            }
        }
        return covered;
    }

    /**
     * True when this node sits strictly under what the level unlocks.
     *
     * <p>A node with no tier is never covered. NULL means "not graded", and
     * completing something on the strength of a missing value is how a data gap
     * turns into a roadmap the student never did — the mirror of the rule
     * {@link RoadmapTierResolver#isLocked} already applies in the other direction.
     */
    private boolean isBelowCeiling(SkillNode node, short ceiling) {
        Short tier = node == null ? null : node.getTier();
        return tier != null && tier < ceiling;
    }
}
