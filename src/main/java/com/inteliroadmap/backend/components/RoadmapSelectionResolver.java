package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.domain.entity.SkillNode;
import com.inteliroadmap.backend.domain.entity.StudentNodeSelection;
import com.inteliroadmap.backend.repositories.StudentNodeSelectionRepository;
import lombok.RequiredArgsConstructor;
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
 * Resolves a student's CHOOSE_ONE selections into the set of roadmap nodes that
 * should be treated as "not on this student's path".
 *
 * For every CHOOSE_ONE group:
 * <ul>
 *   <li>if the student has chosen an alternative, the other alternatives (and
 *       their whole subtrees) are excluded — greyed out as {@code alternative}
 *       and left out of progress;</li>
 *   <li>if the student has not chosen yet, all alternatives are left out of
 *       progress (so the denominator is the personal path, not every language),
 *       but they are NOT greyed out — the frontend still offers them for picking.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class RoadmapSelectionResolver {

    private static final String CHOOSE_ONE = "CHOOSE_ONE";

    private final StudentNodeSelectionRepository selectionRepository;

    /** groupNodeId -&gt; chosenNodeId for the student's stored CHOOSE_ONE decisions. */
    public Map<UUID, UUID> chosenNodeByGroup(UUID studentId) {
        Map<UUID, UUID> chosen = new HashMap<>();
        for (StudentNodeSelection selection : selectionRepository.findByStudent_UserId(studentId)) {
            if (selection.getGroupNode() != null && selection.getChosenNode() != null) {
                chosen.put(selection.getGroupNode().getNodeId(), selection.getChosenNode().getNodeId());
            }
        }
        return chosen;
    }

    /** Convenience overload that loads the selections for the student. */
    public SelectionView resolve(UUID studentId, List<SkillNode> nodes) {
        return resolve(nodes, chosenNodeByGroup(studentId));
    }

    public SelectionView resolve(List<SkillNode> nodes, Map<UUID, UUID> chosenByGroup) {
        Map<UUID, SkillNode> byId = new HashMap<>();
        Map<UUID, List<SkillNode>> childrenByParent = new HashMap<>();
        for (SkillNode node : nodes) {
            byId.put(node.getNodeId(), node);
            if (node.getParentNode() != null) {
                childrenByParent
                        .computeIfAbsent(node.getParentNode().getNodeId(), key -> new ArrayList<>())
                        .add(node);
            }
        }

        Set<UUID> progressExcluded = new HashSet<>();
        Set<UUID> greyedAlternatives = new HashSet<>();
        Set<UUID> offPathDescendants = new HashSet<>();
        for (SkillNode node : nodes) {
            if (node.getParentNode() == null) {
                continue;
            }
            SkillNode group = byId.get(node.getParentNode().getNodeId());
            if (group == null || !CHOOSE_ONE.equalsIgnoreCase(group.getSelection())) {
                continue;
            }
            boolean decided = chosenByGroup.containsKey(group.getNodeId());
            boolean isChosen = decided && node.getNodeId().equals(chosenByGroup.get(group.getNodeId()));
            if (isChosen) {
                continue;
            }
            // Unchosen (or, while undecided, not-yet-chosen) alternative: drop its
            // whole subtree from progress. Only grey it out once the group is decided.
            collectSubtree(node, childrenByParent, progressExcluded);
            if (decided) {
                collectSubtree(node, childrenByParent, greyedAlternatives);
                // Only once the student has actually decided. While the group is
                // undecided every alternative must stay whole, because the student
                // is still choosing between them and cannot choose what was cut.
                for (SkillNode child : childrenByParent.getOrDefault(node.getNodeId(), List.of())) {
                    collectSubtree(child, childrenByParent, offPathDescendants);
                }
            }
        }
        return new SelectionView(progressExcluded, greyedAlternatives, offPathDescendants);
    }

    private void collectSubtree(SkillNode root, Map<UUID, List<SkillNode>> childrenByParent, Set<UUID> out) {
        Deque<SkillNode> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            SkillNode current = stack.pop();
            if (!out.add(current.getNodeId())) {
                continue;
            }
            for (SkillNode child : childrenByParent.getOrDefault(current.getNodeId(), List.of())) {
                stack.push(child);
            }
        }
    }
}
