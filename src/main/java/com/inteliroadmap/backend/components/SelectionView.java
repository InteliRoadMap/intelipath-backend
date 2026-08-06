package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.domain.entity.SkillNode;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Result of resolving a student's CHOOSE_ONE selections against a career's nodes.
 *
 * @param progressExcluded node ids left out of progress math and topic
 *        completion ratios (unchosen alternatives, and all alternatives of an
 *        undecided group)
 * @param greyedAlternatives node ids shown as {@code alternative} in the UI
 *        (unchosen alternatives of a decided group only)
 * @param offPathDescendants what sits <em>below</em> an unchosen alternative of a
 *        decided group — the alternative's own node is not included
 */
public record SelectionView(Set<UUID> progressExcluded, Set<UUID> greyedAlternatives,
                            Set<UUID> offPathDescendants) {

    /**
     * A student who picked Java has no use for Laravel's 49 descendants.
     *
     * <p>Greying them was still sending them: the payload, and the page, carried
     * every language's whole subtree. Dropping the descendants while keeping the
     * alternative's own node means the roadmap holds only the chosen path, and
     * the student can still see what they did not pick and change their mind —
     * which is why this is not simply {@link #greyedAlternatives}.
     */
    public boolean isOffPathDescendant(UUID nodeId) {
        return offPathDescendants.contains(nodeId);
    }

    public boolean isExcludedFromProgress(UUID nodeId) {
        return progressExcluded.contains(nodeId);
    }

    public boolean isGreyedAlternative(UUID nodeId) {
        return greyedAlternatives.contains(nodeId);
    }

    /** The nodes that count toward this student's progress (active path only). */
    public List<SkillNode> activePathNodes(List<SkillNode> nodes) {
        return nodes.stream()
                .filter(node -> !progressExcluded.contains(node.getNodeId()))
                .toList();
    }
}
