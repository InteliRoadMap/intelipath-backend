package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.domain.entity.SkillNode;
import com.inteliroadmap.backend.domain.entity.NodeType;
import com.inteliroadmap.backend.domain.enums.SeniorityLevel;
import com.inteliroadmap.backend.domain.enums.StageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two questions this filter answers — depth and stage — have to stay
 * separable, because one caller wants only the first.
 */
class RoadmapVisibilityFilterTest {

    private RoadmapVisibilityFilter filter;
    private Map<UUID, String> statuses;

    @BeforeEach
    void setUp() {
        filter = new RoadmapVisibilityFilter();
        statuses = new HashMap<>();
    }

    /**
     * What entering a sub-roadmap has to become. Measured on the live catalog,
     * {@code C#} is 269 nodes five levels deep and every one of them was sent at
     * once; only 49 sit within the depth cap.
     */
    @Test
    void depthIsCappedEvenWithTheStageRuleOff() {
        List<SkillNode> nodes = chain(4);

        Set<UUID> visible = filter.visibleNodeIds(
                nodes, statuses, SeniorityLevel.BEGINNER, Set.of(),
                RoadmapVisibilityFilter.DEFAULT_MAX_DEPTH, false);

        assertTrue(visible.contains(nodes.get(0).getNodeId()), "the root");
        assertTrue(visible.contains(nodes.get(1).getNodeId()), "its children");
        assertFalse(visible.contains(nodes.get(2).getNodeId()), "two levels down waits for an expand");
        assertFalse(visible.contains(nodes.get(3).getNodeId()));
    }

    /**
     * The reason the flag exists. Inside a track the student opened on purpose,
     * an advanced node is reported through {@code tierLocked} — which says "not
     * yet" while still showing the road continues. Removing it as well would make
     * the same point twice, the second time by making the roadmap look shorter
     * than it is.
     */
    @Test
    void stageRuleOffKeepsAdvancedContentAtTheSameDepth() {
        SkillNode root = node("Root", null, StageType.FOUNDATION);
        SkillNode advanced = node("Concurrency", root, StageType.ADVANCED);
        List<SkillNode> nodes = List.of(root, advanced);

        Set<UUID> withStageRule = filter.visibleNodeIds(
                nodes, statuses, SeniorityLevel.BEGINNER, Set.of(),
                RoadmapVisibilityFilter.DEFAULT_MAX_DEPTH, true);
        Set<UUID> withoutStageRule = filter.visibleNodeIds(
                nodes, statuses, SeniorityLevel.BEGINNER, Set.of(),
                RoadmapVisibilityFilter.DEFAULT_MAX_DEPTH, false);

        assertFalse(withStageRule.contains(advanced.getNodeId()),
                "the career view still paces a beginner by stage");
        assertTrue(withoutStageRule.contains(advanced.getNodeId()),
                "if this is hidden too, the flag is doing nothing");
    }

    /** The default overload must behave exactly as it did before the flag existed. */
    @Test
    void theFiveArgumentOverloadStillAppliesTheStageRule() {
        SkillNode root = node("Root", null, StageType.FOUNDATION);
        SkillNode advanced = node("Concurrency", root, StageType.ADVANCED);
        List<SkillNode> nodes = List.of(root, advanced);

        assertEquals(
                filter.visibleNodeIds(nodes, statuses, SeniorityLevel.BEGINNER, Set.of(),
                        RoadmapVisibilityFilter.DEFAULT_MAX_DEPTH, true),
                filter.visibleNodeIds(nodes, statuses, SeniorityLevel.BEGINNER, Set.of(),
                        RoadmapVisibilityFilter.DEFAULT_MAX_DEPTH));
    }

    /**
     * An expand has to reach past the cap in a sub-roadmap too, or the "+N" badge
     * is a button that does nothing — the exact failure the sub-roadmap endpoint
     * was written to avoid in the first place.
     */
    @Test
    void anExpandReachesPastTheCap() {
        List<SkillNode> nodes = chain(4);

        Set<UUID> visible = filter.visibleNodeIds(
                nodes, statuses, SeniorityLevel.BEGINNER, Set.of(nodes.get(1).getNodeId()),
                RoadmapVisibilityFilter.DEFAULT_MAX_DEPTH, false);

        assertTrue(visible.contains(nodes.get(2).getNodeId()));
        assertTrue(visible.contains(nodes.get(3).getNodeId()), "all the way down, not one level");
    }

    /** What the folded-away children are counted as, so the badge can offer them. */
    @Test
    void hiddenChildrenAreCountedForTheirParent() {
        List<SkillNode> nodes = chain(3);
        Set<UUID> visible = filter.visibleNodeIds(
                nodes, statuses, SeniorityLevel.BEGINNER, Set.of(),
                RoadmapVisibilityFilter.DEFAULT_MAX_DEPTH, false);

        Map<UUID, Integer> hidden = filter.hiddenChildCounts(nodes, visible);

        assertEquals(1, hidden.get(nodes.get(1).getNodeId()));
    }

    /** A parent -> child -> grandchild... chain of the given length. */
    private List<SkillNode> chain(int length) {
        List<SkillNode> nodes = new ArrayList<>();
        SkillNode parent = null;
        for (int i = 0; i < length; i++) {
            parent = node("n" + i, parent, StageType.FOUNDATION);
            nodes.add(parent);
        }
        return nodes;
    }

    private SkillNode node(String name, SkillNode parent, StageType stage) {
        return SkillNode.builder()
                .nodeId(UUID.randomUUID())
                .nodeName(name)
                .parentNode(parent)
                .type(NodeType.builder().stage(stage).build())
                .build();
    }
}
