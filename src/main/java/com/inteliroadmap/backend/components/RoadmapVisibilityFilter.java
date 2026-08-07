package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.domain.entity.SkillNode;
import com.inteliroadmap.backend.domain.enums.SeniorityLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Decides which of a career's nodes are worth sending to one student.
 *
 * <p>Why this exists: the graded pool now carries real depth — Backend alone
 * holds 1.678 nodes across 8 levels — and the old contract shipped every one of
 * them on every page load. Measured on the Frontend career, 538 nodes came to
 * 772 KB and 836 edges; Backend would be roughly three times that, all of it
 * rendered at once by a canvas the student can only look at a screenful of.
 *
 * <p>The filter answers two different questions, and keeps them separate on
 * purpose:
 *
 * <ul>
 *   <li><b>Depth</b> — a sub-skill four levels down is not a thing a student
 *       browses, it is a thing they descend into once they pick the topic above
 *       it. Those arrive from the expand endpoint instead.</li>
 *   <li><b>Level</b> — an ADVANCED node under a topic is not wrong for a
 *       FRESHER, only badly timed. Hidden while it is far above them, and it
 *       comes back as they progress.</li>
 * </ul>
 *
 * <p>Two rules are deliberate and should survive review:
 *
 * <ul>
 *   <li><b>Nothing completed is ever hidden.</b> Work already done stays
 *       visible whatever its depth or stage, because hiding it would read as
 *       losing it.</li>
 *   <li><b>The stage gate opens on finished work, not only on the assessment.</b>
 *       A student who completes all of FOUNDATION sees CORE immediately, rather
 *       than waiting for a level that only moves when they re-take a test.</li>
 * </ul>
 */
@Component
@Slf4j
public class RoadmapVisibilityFilter {

    /**
     * Depth kept in the default payload: roots and their direct children.
     *
     * <p>Deeper than this and a student is looking at the inside of a topic they
     * have not chosen yet — which is exactly the request the expand endpoint
     * serves.
     */
    public static final int DEFAULT_MAX_DEPTH = 2;

    /**
     * How many stage bands above the student a node may sit before it is held
     * back. One, not zero: the next band is what they are working toward, and
     * hiding it would leave them with no visible direction.
     */
    private static final int STAGE_LOOKAHEAD = 1;

    private static final String COMPLETED = "completed";

    /**
     * @param nodes every node of the career
     * @param statusByNodeId statuses already computed over the FULL set — this
     *        filter never recomputes them, so what it hides cannot change what a
     *        visible node says about itself
     * @param level the student's level, or null when they skipped the assessment,
     *        in which case the stage rule switches itself off rather than guessing
     * @param expandedNodeIds subtrees the student explicitly opened; their
     *        descendants bypass the depth rule
     * @return the node ids to serialise
     */
    public Set<UUID> visibleNodeIds(List<SkillNode> nodes,
                                    Map<UUID, String> statusByNodeId,
                                    SeniorityLevel level,
                                    Set<UUID> expandedNodeIds,
                                    int maxDepth) {
        return visibleNodeIds(nodes, statusByNodeId, level, expandedNodeIds, maxDepth, true);
    }

    /**
     * The same filter with the stage rule switchable.
     *
     * <p>The two questions in the class javadoc are kept separate on purpose, and
     * one caller wants only one of them. Inside a sub-roadmap — a language or
     * framework track the student opened deliberately — depth still has to be
     * capped, because C# is 269 nodes and no canvas draws that usefully. But
     * nothing there is withheld for being above the student's level: that track
     * already carries {@code tierLocked} on every node, which says "not yet" while
     * still showing that the road continues. Removing the same nodes as well would
     * be making the point twice, the second time by lying about the roadmap's
     * length.
     *
     * @param applyStageRule false to cap depth only and judge nothing by stage
     */
    public Set<UUID> visibleNodeIds(List<SkillNode> nodes,
                                    Map<UUID, String> statusByNodeId,
                                    SeniorityLevel level,
                                    Set<UUID> expandedNodeIds,
                                    int maxDepth,
                                    boolean applyStageRule) {
        Map<UUID, Integer> depthByNodeId = depths(nodes);
        Set<UUID> expanded = expandedNodeIds == null ? Set.of() : expandedNodeIds;
        int allowedStage = applyStageRule
                ? allowedStageIndex(nodes, statusByNodeId, level)
                : Integer.MAX_VALUE;

        Set<UUID> visible = new HashSet<>();
        for (SkillNode node : nodes) {
            UUID nodeId = node.getNodeId();
            int depth = depthByNodeId.getOrDefault(nodeId, 0);

            // Roots carry the shape of the roadmap, but "never filtered" was too
            // strong: Backend's 23 roots run FOUNDATION(12) → JOB_READY(2), so a
            // FRESHER was handed the whole five-stage career on day one. A root
            // more than one stage ahead is held back until they get there.
            if (depth == 0) {
                if (!isAboveAllowedStage(node, allowedStage)) {
                    visible.add(nodeId);
                }
                continue;
            }
            // Hiding finished work reads as losing it.
            if (COMPLETED.equals(statusByNodeId.get(nodeId))) {
                visible.add(nodeId);
                continue;
            }
            // Expand overrides depth, never the capability stage. Otherwise a
            // Fresher can click "+N" once and receive the same advanced payload
            // as a Senior, defeating personalization in both career and
            // sub-roadmap views.
            if (hasExpandedAncestor(node, expanded)) {
                if (!isAboveAllowedStage(node, allowedStage)) {
                    visible.add(nodeId);
                }
                continue;
            }
            if (depth >= maxDepth) {
                continue;
            }
            if (isAboveAllowedStage(node, allowedStage)) {
                continue;
            }
            visible.add(nodeId);
        }

        // A visible node whose parent was filtered out would render as an orphan
        // root. Pull the ancestors back in rather than dropping the node.
        for (SkillNode node : nodes) {
            if (!visible.contains(node.getNodeId())) {
                continue;
            }
            for (SkillNode ancestor = node.getParentNode();
                 ancestor != null && !visible.contains(ancestor.getNodeId());
                 ancestor = ancestor.getParentNode()) {
                visible.add(ancestor.getNodeId());
            }
        }
        return visible;
    }

    /** Children of {@code nodeId} that the filter left out, so the UI can offer them. */
    public Map<UUID, Integer> hiddenChildCounts(List<SkillNode> nodes, Set<UUID> visible) {
        Map<UUID, Integer> hidden = new HashMap<>();
        for (SkillNode node : nodes) {
            if (node.getParentNode() == null || visible.contains(node.getNodeId())) {
                continue;
            }
            hidden.merge(node.getParentNode().getNodeId(), 1, Integer::sum);
        }
        return hidden;
    }

    /** Distance from the nearest root; cycles in the data settle at the cap rather than hanging. */
    private Map<UUID, Integer> depths(List<SkillNode> nodes) {
        Map<UUID, Integer> depthByNodeId = new HashMap<>();
        for (SkillNode node : nodes) {
            int depth = 0;
            List<UUID> seen = new ArrayList<>();
            for (SkillNode cursor = node.getParentNode();
                 cursor != null && depth < 32 && !seen.contains(cursor.getNodeId());
                 cursor = cursor.getParentNode()) {
                seen.add(cursor.getNodeId());
                depth++;
            }
            depthByNodeId.put(node.getNodeId(), depth);
        }
        return depthByNodeId;
    }

    private boolean hasExpandedAncestor(SkillNode node, Set<UUID> expanded) {
        for (SkillNode cursor = node.getParentNode(); cursor != null; cursor = cursor.getParentNode()) {
            if (expanded.contains(cursor.getNodeId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The furthest stage this student may see.
     *
     * <p>Two sources, whichever is further along, because they answer different
     * questions. The assessment says where they <em>started</em>; the work they
     * have actually completed says where they <em>are</em>. Using the assessment
     * alone would leave a student who finished all of FOUNDATION still staring at
     * FOUNDATION, waiting on a level that only moves when they re-take a test.
     *
     * <p>Returns {@link Integer#MAX_VALUE} for a student with no level and no
     * completed work — no claim has been made about them, so nothing is withheld.
     */
    private int allowedStageIndex(List<SkillNode> nodes,
                                  Map<UUID, String> statusByNodeId,
                                  SeniorityLevel level) {
        int reached = -1;
        for (SkillNode node : nodes) {
            if (!COMPLETED.equals(statusByNodeId.get(node.getNodeId()))) {
                continue;
            }
            Integer stageIndex = stageIndexOf(node);
            if (stageIndex != null) {
                reached = Math.max(reached, stageIndex);
            }
        }

        Integer fromLevel = level == null || level == SeniorityLevel.UNKNOWN ? null : switch (level) {
            case BEGINNER, FRESHER -> 0;
            case JUNIOR -> 1;
            case MID -> 2;
            case SENIOR -> 3;
            case EXPERT, UNKNOWN -> 4;
        };

        if (fromLevel == null && reached < 0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(fromLevel == null ? 0 : fromLevel, reached) + STAGE_LOOKAHEAD;
    }

    /** True when the node's stage sits further ahead than the student may see. */
    private boolean isAboveAllowedStage(SkillNode node, int allowedStage) {
        if (allowedStage == Integer.MAX_VALUE) {
            return false;
        }
        Integer stageIndex = stageIndexOf(node);
        // A node with no stage cannot be judged, so it is never withheld.
        return stageIndex != null && stageIndex > allowedStage;
    }

    private Integer stageIndexOf(SkillNode node) {
        if (node.getType() == null || node.getType().getStage() == null) {
            return null;
        }
        return switch (node.getType().getStage()) {
            case FOUNDATION -> 0;
            case CORE -> 1;
            case PRACTICAL -> 2;
            case ADVANCED -> 3;
            case JOB_READY -> 4;
        };
    }
}
