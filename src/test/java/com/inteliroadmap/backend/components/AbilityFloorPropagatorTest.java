package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.domain.entity.SkillNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tier bound is the entire safety of this class, so most of what is asserted
 * here is what it refuses to cover.
 */
class AbilityFloorPropagatorTest {

    private final AbilityFloorPropagator propagator = new AbilityFloorPropagator(new RoadmapTierResolver());

    private static SkillNode node(String name, Short tier, SkillNode parent) {
        return SkillNode.builder()
                .nodeId(UUID.randomUUID())
                .nodeName(name)
                .tier(tier)
                .parentNode(parent)
                .build();
    }

    private static List<String> names(List<SkillNode> nodes) {
        return nodes.stream().map(SkillNode::getNodeName).sorted().toList();
    }

    /**
     * The shape measured on the live database: Java is tier 1, and its subtree is
     * entirely tier 1 and tier 2.
     */
    private record JavaTree(SkillNode java, SkillNode exceptions, SkillNode optionals,
                            SkillNode moreOop, SkillNode oopBasics, SkillNode concurrency,
                            List<SkillNode> all) {}

    private static JavaTree javaTree() {
        SkillNode java = node("Java", (short) 1, null);
        SkillNode exceptions = node("Exception Handling", (short) 1, java);
        SkillNode optionals = node("Optionals", (short) 1, java);
        SkillNode moreOop = node("More about OOP", (short) 2, java);
        // A tier-1 child under a tier-2 heading. The walk must reach it.
        SkillNode oopBasics = node("Basics of OOP", (short) 1, moreOop);
        SkillNode concurrency = node("Concurrency", (short) 2, java);
        return new JavaTree(java, exceptions, optionals, moreOop, oopBasics, concurrency,
                List.of(java, exceptions, optionals, moreOop, oopBasics, concurrency));
    }

    @Test
    void aFresherCoversTheBasicsOfASkillTheyHaveProven() {
        JavaTree tree = javaTree();

        List<SkillNode> covered = propagator.coveredDescendants(
                Set.of(tree.java().getNodeId()), tree.all(), "FRESHER");

        // Tier 1 only. The tier-2 nodes are what is still left to learn.
        assertEquals(List.of("Basics of OOP", "Exception Handling", "Optionals"), names(covered));
    }

    /**
     * The ceiling is one tier generous on purpose — it exists so a student can SEE
     * where the road goes. Covering everything up to it would complete everything
     * they can see, which on the real Java subtree is all 72 nodes.
     */
    @Test
    void theCeilingTierIsNeverCovered() {
        JavaTree tree = javaTree();

        List<SkillNode> covered = propagator.coveredDescendants(
                Set.of(tree.java().getNodeId()), tree.all(), "FRESHER");

        assertTrue(covered.stream().noneMatch(n -> n.getTier() != null && n.getTier() >= 2),
                "FRESHER unlocks tier 2, so tier 2 must remain something to do");
    }

    @Test
    void aHigherLevelCoversMore() {
        JavaTree tree = javaTree();

        // JUNIOR's ceiling is tier 3, so tier 2 falls below it.
        List<SkillNode> covered = propagator.coveredDescendants(
                Set.of(tree.java().getNodeId()), tree.all(), "JUNIOR");

        assertEquals(
                List.of("Basics of OOP", "Concurrency", "Exception Handling", "More about OOP", "Optionals"),
                names(covered));
    }

    /**
     * An unassessed student propagates nothing. RoadmapTierResolver answers "no
     * level" with the most generous ceiling, which is right for showing a roadmap
     * and would be backwards here — the largest auto-completion handed to the
     * student we know least about.
     */
    @Test
    void noLevelCoversNothing() {
        JavaTree tree = javaTree();

        assertTrue(propagator.coveredDescendants(Set.of(tree.java().getNodeId()), tree.all(), null).isEmpty());
        assertTrue(propagator.coveredDescendants(Set.of(tree.java().getNodeId()), tree.all(), "  ").isEmpty());
    }

    /** A node nobody graded is not covered: a missing tier is not a low tier. */
    @Test
    void anUngradedNodeIsNeverCovered() {
        SkillNode root = node("Java", (short) 1, null);
        SkillNode ungraded = node("Something nobody graded", null, root);

        List<SkillNode> covered = propagator.coveredDescendants(
                Set.of(root.getNodeId()), List.of(root, ungraded), "JUNIOR");

        assertTrue(covered.isEmpty());
    }

    /** The proven node itself is not among its own descendants. */
    @Test
    void theProvenNodeIsNotReturned() {
        JavaTree tree = javaTree();

        List<SkillNode> covered = propagator.coveredDescendants(
                Set.of(tree.java().getNodeId()), tree.all(), "JUNIOR");

        assertTrue(covered.stream().noneMatch(n -> n.getNodeId().equals(tree.java().getNodeId())));
    }

    @Test
    void nothingProvenCoversNothing() {
        JavaTree tree = javaTree();

        assertTrue(propagator.coveredDescendants(Set.of(), tree.all(), "SENIOR").isEmpty());
        assertTrue(propagator.coveredDescendants(null, tree.all(), "SENIOR").isEmpty());
        assertTrue(propagator.coveredDescendants(Set.of(tree.java().getNodeId()), List.of(), "SENIOR").isEmpty());
    }

    /**
     * The discount is what keeps inherited evidence weaker than the evidence it
     * came from. These are the three numbers that decide the feature's behaviour
     * against a 0.55 node bar.
     */
    @Test
    void inheritedEvidenceIsWeakerThanDirect() {
        // GitHub-verified, 0.85 -> 0.7225: clears a 0.55 bar.
        assertTrue(propagator.inheritedConfidence(new BigDecimal("0.85"))
                .compareTo(new BigDecimal("0.55")) > 0);
        // APPLIED self-declaration, 0.70 -> 0.595: still clears it.
        assertTrue(propagator.inheritedConfidence(new BigDecimal("0.70"))
                .compareTo(new BigDecimal("0.55")) > 0);
        // PRACTICED, 0.55 -> 0.4675: does not. Having practised something is not
        // grounds to skip learning what is under it.
        assertTrue(propagator.inheritedConfidence(new BigDecimal("0.55"))
                .compareTo(new BigDecimal("0.55")) < 0);
    }

    @Test
    void aMissingConfidencePropagatesNothing() {
        assertEquals(null, propagator.inheritedConfidence(null));
    }
}
