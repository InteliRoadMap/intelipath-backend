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
 */
public record SelectionView(Set<UUID> progressExcluded, Set<UUID> greyedAlternatives) {

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
