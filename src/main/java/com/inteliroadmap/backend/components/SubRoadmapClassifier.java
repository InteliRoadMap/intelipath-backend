package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.domain.entity.SkillNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
 * Decides which nodes are roadmaps in their own right.
 *
 * <p>Some nodes are a step: read it, do it, move on. Others are an entire
 * curriculum wearing a node's clothes — Java carries 71 descendants, Python 122,
 * ASP.NET Core 157. Rendering those inline is what produced a page that scrolled
 * for a minute and still had "+120 more" on it. They are places to go, not steps
 * to walk past.
 *
 * <p>The first version of this keyed on {@code parent_node IS NULL AND
 * node_level = 0}, which caught the nine imported roadmaps sitting at Backend's
 * root and missed Java entirely — Java hangs under "Pick a Language", so it is
 * not a root, and it is exactly the node a student clicks first. Size is the
 * honest signal: what makes something a roadmap is how much is inside it, not
 * where it happens to be attached.
 */
@Component
@Slf4j
public class SubRoadmapClassifier {

    /**
     * Descendants a node needs before it becomes somewhere to enter.
     *
     * <p>Twelve is roughly where an inline list stops being scannable at a glance.
     * Below it, expanding in place is less disruptive than replacing the screen;
     * above it, the expansion buries whatever came after it on the path.
     */
    public static final int MIN_SUBTREE = 12;

    /** A group whose children are alternatives; never a roadmap of its own. */
    private static final String CHOOSE_ONE = "CHOOSE_ONE";

    /** Direct children of each node, built once per call. */
    private Map<UUID, List<SkillNode>> childrenByParent(List<SkillNode> nodes) {
        Map<UUID, List<SkillNode>> byParent = new HashMap<>();
        for (SkillNode node : nodes) {
            if (node.getParentNode() != null) {
                byParent.computeIfAbsent(node.getParentNode().getNodeId(), key -> new ArrayList<>())
                        .add(node);
            }
        }
        return byParent;
    }

    /**
     * Descendant count for every node, excluding the node itself.
     *
     * <p>Reads the stored column where it exists. Counting per request would give
     * a different answer inside a sub-roadmap view — there the subtree is all the
     * caller loaded — and a node that claims 71 on one screen and 24 on the next
     * is telling the student two different things about the same click.
     */
    public Map<UUID, Integer> subtreeSizes(List<SkillNode> nodes) {
        Map<UUID, List<SkillNode>> byParent = childrenByParent(nodes);
        Map<UUID, Integer> sizes = new HashMap<>();
        for (SkillNode node : nodes) {
            sizes.put(node.getNodeId(), node.getSubtreeSize() != null
                    ? node.getSubtreeSize()
                    : descendants(node, byParent).size());
        }
        return sizes;
    }

    /**
     * Nodes big enough to be opened as their own roadmap.
     *
     * <p><b>A CHOOSE_ONE group never qualifies, whatever its size.</b> Size is
     * the wrong question for a group: `Pick a Framework` sums to 70 because
     * React is 42 and Vue is 32, and none of that is content the group teaches
     * — it is content the options teach, only one of which the student takes.
     * Treating it as enterable withheld its own options from the payload, so
     * the group rendered with nothing to choose between and the whole decision
     * disappeared behind a "go deeper" chip. Meanwhile `Package Managers` (4)
     * fell under the threshold and worked, which is how the bug hid: the
     * choosers that broke were exactly the ones with real tracks behind them.
     *
     * <p>The options themselves stay enterable. React's 42 nodes are a roadmap;
     * the question "React or Vue?" is not.
     */
    public Set<UUID> enterableNodes(List<SkillNode> nodes) {
        Map<UUID, Integer> sizes = subtreeSizes(nodes);
        Set<UUID> choiceGroups = new HashSet<>();
        for (SkillNode node : nodes) {
            if (CHOOSE_ONE.equalsIgnoreCase(node.getSelection())) {
                choiceGroups.add(node.getNodeId());
            }
        }
        Set<UUID> enterable = new HashSet<>();
        sizes.forEach((nodeId, size) -> {
            if (size >= MIN_SUBTREE && !choiceGroups.contains(nodeId)) {
                enterable.add(nodeId);
            }
        });
        return enterable;
    }

    /**
     * Everything that lives inside an enterable node, and so should not be drawn
     * on the path outside it.
     *
     * <p>Only descendants are ever withheld — the enterable node itself always
     * stays. That is what makes this safe to apply everywhere: no career can be
     * emptied by it, including Data Science, which is six imported roadmaps and
     * no path of its own.
     *
     * @param keep a node the student is following, whose contents stay inline
     *        (their chosen language belongs on the path, not behind a click)
     */
    public Set<UUID> nodesInsideEnterables(List<SkillNode> nodes, UUID keep) {
        Map<UUID, List<SkillNode>> byParent = childrenByParent(nodes);
        Set<UUID> enterable = enterableNodes(nodes);
        Set<UUID> hidden = new HashSet<>();

        for (SkillNode node : nodes) {
            if (!enterable.contains(node.getNodeId()) || node.getNodeId().equals(keep)) {
                continue;
            }
            hidden.addAll(descendants(node, byParent));
        }
        // A kept node's own contents stay, even where an ancestor is enterable:
        // the student asked to follow this track, so it is part of their path.
        if (keep != null) {
            SkillNode kept = nodes.stream()
                    .filter(n -> n.getNodeId().equals(keep))
                    .findFirst().orElse(null);
            if (kept != null) {
                hidden.removeAll(descendants(kept, byParent));
                hidden.remove(keep);
            }
        }
        return hidden;
    }

    private Set<UUID> descendants(SkillNode root, Map<UUID, List<SkillNode>> byParent) {
        Set<UUID> out = new HashSet<>();
        Deque<SkillNode> pending = new ArrayDeque<>(byParent.getOrDefault(root.getNodeId(), List.of()));
        while (!pending.isEmpty()) {
            SkillNode current = pending.pop();
            if (!out.add(current.getNodeId())) {
                continue;
            }
            pending.addAll(byParent.getOrDefault(current.getNodeId(), List.of()));
        }
        return out;
    }
}
