package com.inteliroadmap.backend.components;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The per-student ordering of a roadmap: what replaces the static
 * {@code previous_node} column.
 *
 * @param previousByNodeId node id → the node that must come before it. Read in
 *        place of {@code SkillNode#getPreviousNode()} by both the unlock gate and
 *        the API contract, so display order and unlock order stay the same thing.
 * @param visitOrder every node id, dependencies first. Statuses <em>must</em> be
 *        computed in this order — walking the nodes in database order instead
 *        leaves a predecessor unclassified and locks everything behind it.
 * @param edges the graph as the frontend draws it, each carrying its reason
 */
public record ResolvedOrder(
        Map<UUID, UUID> previousByNodeId,
        List<UUID> visitOrder,
        List<RoadmapEdge> edges) {
}
