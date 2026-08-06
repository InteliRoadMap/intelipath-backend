package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.domain.entity.SkillNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadmapTierResolverTest {

    private RoadmapTierResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new RoadmapTierResolver();
    }

    @Test
    void aBeginnerSeesOneTierBeyondTheirOwn() {
        // The next thing you learn is by definition the thing you cannot do yet,
        // so the tier above the student's own must stay open.
        assertEquals(RoadmapTierResolver.TIER_INTERMEDIATE, resolver.ceilingFor("BEGINNER"));
        assertFalse(resolver.isLocked(tier(2), resolver.ceilingFor("BEGINNER")));
        assertTrue(resolver.isLocked(tier(3), resolver.ceilingFor("BEGINNER")));
    }

    @Test
    void aJuniorSeesEverything() {
        assertEquals(RoadmapTierResolver.TIER_ADVANCED, resolver.ceilingFor("JUNIOR"));
        assertFalse(resolver.isLocked(tier(3), resolver.ceilingFor("JUNIOR")));
    }

    /**
     * "We have not assessed you" is not "you are a beginner". An unassessed
     * student handed the smallest roadmap would be punished for skipping a step
     * the product lets them skip.
     */
    @Test
    void noLevelYetLocksNothing() {
        short ceiling = resolver.ceilingFor(null);
        assertFalse(resolver.isLocked(tier(3), ceiling));
        assertFalse(resolver.isLocked(tier(2), ceiling));
    }

    /**
     * NULL tier means "not graded". Withholding content on a missing value is
     * how a data gap turns into a shorter roadmap nobody ordered — and tier was
     * NULL on the whole catalog until the migration that introduced it.
     */
    @Test
    void anUngradedNodeIsNeverLocked() {
        assertFalse(resolver.isLocked(tier(null), RoadmapTierResolver.TIER_BEGINNER));
    }

    @Test
    void changingLevelChangesHowMuchIsUnlocked() {
        List<SkillNode> nodes = List.of(tier(1), tier(2), tier(3), tier(3), tier(null));

        long asBeginner = resolver.activeCount(nodes, resolver.ceilingFor("BEGINNER"));
        long asSenior = resolver.activeCount(nodes, resolver.ceilingFor("SENIOR"));

        assertEquals(3, asBeginner, "tier 1, tier 2 and the ungraded node");
        assertEquals(5, asSenior);
        assertTrue(asSenior > asBeginner, "if these are equal the tier column is doing nothing");
    }

    /**
     * Regression for the bug the UI exposed: `Pick a Framework` sums to 70
     * because React is 42 and Vue is 32, so it crossed MIN_SUBTREE, was filed
     * as an enterable roadmap, and had its own options withheld from the
     * payload — the chooser rendered with nothing to choose between. Small
     * groups like `Package Managers` (4) worked, which is why it hid so long.
     */
    @Test
    void aChoiceGroupIsNeverEnterableHoweverLargeItsOptionsAre() {
        SubRoadmapClassifier classifier = new SubRoadmapClassifier();
        SkillNode group = SkillNode.builder()
                .nodeId(UUID.randomUUID()).nodeName("Pick a Framework")
                .selection("CHOOSE_ONE").subtreeSize(70).build();
        SkillNode react = SkillNode.builder()
                .nodeId(UUID.randomUUID()).nodeName("React")
                .parentNode(group).selection("ALL").subtreeSize(42).build();

        var enterable = classifier.enterableNodes(List.of(group, react));

        assertFalse(enterable.contains(group.getNodeId()), "the question is not a roadmap");
        assertTrue(enterable.contains(react.getNodeId()), "but the answer is");
    }

    private SkillNode tier(Integer tier) {
        return SkillNode.builder()
                .nodeId(UUID.randomUUID())
                .tier(tier == null ? null : tier.shortValue())
                .build();
    }
}
