package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.domain.entity.SkillNode;
import com.inteliroadmap.backend.domain.entity.StudentProgress;
import com.inteliroadmap.backend.domain.enums.RoadmapStepStatus;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Single source of truth for roadmap progress percentages.
 *
 * Formula: round(sum(weight of COMPLETED nodes) / sum(weight of all nodes) * 100)
 * where a node's weight comes from its {@code NodeType.weight} and falls back
 * to 1 when the type or weight is missing. Every service that reports a
 * roadmap percentage must delegate here so the numbers stay consistent.
 */
@Component
public class RoadmapProgressCalculator {

    private static final int DEFAULT_NODE_WEIGHT = 1;
    private static final String NEVER_COMPLETE_POLICY = "NEVER_COMPLETE";

    public int calculateProgress(List<SkillNode> nodes, List<StudentProgress> progresses) {
        return calculateProgress(nodes, mapProgressByNodeId(progresses));
    }

    public int calculateProgress(List<SkillNode> nodes, Map<UUID, StudentProgress> progressByNodeId) {
        if (nodes == null || nodes.isEmpty()) {
            return 0;
        }

        long totalWeight = 0;
        long completedWeight = 0;
        for (SkillNode node : nodes) {
            // NEVER_COMPLETE nodes are group headers that can never be completed;
            // counting them would cap progress permanently below 100%.
            if (NEVER_COMPLETE_POLICY.equalsIgnoreCase(node.getCompletionPolicy())) {
                continue;
            }
            int weight = resolveWeight(node);
            totalWeight += weight;
            if (isCompleted(progressByNodeId.get(node.getNodeId()))) {
                completedWeight += weight;
            }
        }

        if (totalWeight == 0) {
            return 0;
        }
        return (int) Math.round((completedWeight * 100.0) / totalWeight);
    }

    public Map<UUID, StudentProgress> mapProgressByNodeId(List<StudentProgress> progresses) {
        Map<UUID, StudentProgress> progressByNodeId = new HashMap<>();
        if (progresses == null) {
            return progressByNodeId;
        }
        for (StudentProgress progress : progresses) {
            progressByNodeId.put(progress.getSkillNode().getNodeId(), progress);
        }
        return progressByNodeId;
    }

    private boolean isCompleted(StudentProgress progress) {
        return progress != null && progress.getStatus() == RoadmapStepStatus.COMPLETED;
    }

    private int resolveWeight(SkillNode node) {
        if (node.getType() != null && node.getType().getWeight() != null && node.getType().getWeight() > 0) {
            return node.getType().getWeight();
        }
        return DEFAULT_NODE_WEIGHT;
    }
}
