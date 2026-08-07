package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.domain.entity.SkillNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubRoadmapClassifierTest {

    private SubRoadmapClassifier classifier;

    private SkillNode pickLanguage; // spine topic
    private SkillNode java;         // 20 descendants, NOT a root
    private SkillNode csharp;       // 20 descendants, NOT a root
    private SkillNode versionControl; // spine topic, 2 descendants
    private List<SkillNode> nodes;

    @BeforeEach
    void setUp() {
        classifier = new SubRoadmapClassifier();
        nodes = new ArrayList<>();

        pickLanguage = node("Pick a Language", null);
        java = node("Java", pickLanguage);
        csharp = node("C#", pickLanguage);
        versionControl = node("Version Control", null);
        node("Git", versionControl);
        node("GitHub", versionControl);

        for (int i = 0; i < 20; i++) {
            node("java-topic-" + i, java);
            node("csharp-topic-" + i, csharp);
        }
    }

    private SkillNode node(String name, SkillNode parent) {
        SkillNode created = SkillNode.builder()
                .nodeId(UUID.randomUUID()).nodeName(name).parentNode(parent).build();
        nodes.add(created);
        return created;
    }

    /**
     * The miss that motivated the rewrite: the first rule keyed on "root node with
     * node_level 0", which caught the imported roadmaps sitting at Backend's root
     * and missed Java completely — Java hangs under "Pick a Language", and it is
     * the node a student clicks first.
     */
    @Test
    void aDeepNodeIsEnterableEvenThoughItIsNotARoot() {
        Set<UUID> enterable = classifier.enterableNodes(nodes);

        assertTrue(enterable.contains(java.getNodeId()));
        assertTrue(enterable.contains(csharp.getNodeId()));
    }

    @Test
    void aShallowTopicStaysInline() {
        assertFalse(classifier.enterableNodes(nodes).contains(versionControl.getNodeId()));
    }

    @Test
    void theContentsOfAnEnterableNodeComeOffThePath() {
        Set<UUID> hidden = classifier.nodesInsideEnterables(nodes, null);

        // Java's 20 topics live behind the click…
        assertTrue(hidden.containsAll(
                nodes.stream()
                        .filter(n -> n.getNodeName().startsWith("java-topic-"))
                        .map(SkillNode::getNodeId)
                        .toList()));
        // …and so does Java itself, because "Pick a Language" is large enough to be
        // enterable too and Java sits inside it. That is the intended shape: the
        // path shows "Pick a Language", entering it shows the languages, entering
        // Java shows Java's own roadmap. Two levels, the way roadmap.sh reads.
        assertTrue(hidden.contains(java.getNodeId()));
        // The outermost enterable node always survives — this is what stops the
        // rule from ever emptying a career.
        assertFalse(hidden.contains(pickLanguage.getNodeId()));
    }

    /** The track the student follows belongs on their path, not behind a click. */
    @Test
    void theKeptNodeKeepsItsContentsInline() {
        Set<UUID> hidden = classifier.nodesInsideEnterables(nodes, java.getNodeId());

        assertFalse(hidden.contains(
                nodes.stream().filter(n -> "java-topic-0".equals(n.getNodeName()))
                        .findFirst().orElseThrow().getNodeId()));
        assertTrue(hidden.contains(
                nodes.stream().filter(n -> "csharp-topic-0".equals(n.getNodeName()))
                        .findFirst().orElseThrow().getNodeId()));
    }

    @Test
    void shallowSubtreesAreNeverHidden() {
        Set<UUID> hidden = classifier.nodesInsideEnterables(nodes, null);

        assertFalse(hidden.contains(
                nodes.stream().filter(n -> "Git".equals(n.getNodeName()))
                        .findFirst().orElseThrow().getNodeId()));
    }

    @Test
    void subtreeSizeCountsDescendantsNotTheNodeItself() {
        assertEquals(20, classifier.subtreeSizes(nodes).get(java.getNodeId()));
        assertEquals(2, classifier.subtreeSizes(nodes).get(versionControl.getNodeId()));
    }

    /** "Pick a Language" holds both language subtrees, so it is enterable too. */
    @Test
    void anAncestorOfEnterablesIsItselfEnterable() {
        assertTrue(classifier.enterableNodes(nodes).contains(pickLanguage.getNodeId()));
    }
}
