package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.request.NodePosition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.inteliroadmap.backend.domain.dto.request.SaveNodePositionsRequest;
import com.inteliroadmap.backend.domain.dto.request.UpsertRoadmapNodeRequest;
import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapNodeResponse;
import com.inteliroadmap.backend.domain.entity.CareerRole;
import com.inteliroadmap.backend.domain.entity.NodeType;
import com.inteliroadmap.backend.domain.entity.RoadmapNodeLayout;
import com.inteliroadmap.backend.domain.entity.SkillNode;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.domain.enums.StageType;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.repositories.CareerRoleRepository;
import com.inteliroadmap.backend.repositories.NodeTypeRepository;
import com.inteliroadmap.backend.repositories.RoadmapNodeLayoutRepository;
import com.inteliroadmap.backend.repositories.SkillNodeRepository;
import com.inteliroadmap.backend.repositories.StudentProgressRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.services.RoadmapEditorService;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of {@link RoadmapEditorService}.
 *
 * Roadmap logic (parent/previous/stage/policy/proficiency) lives on
 * {@link SkillNode}; presentation-only placement lives on
 * {@link RoadmapNodeLayout}. The two are edited here but kept strictly apart,
 * so dragging a node never changes unlock/progress.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoadmapEditorServiceImpl implements RoadmapEditorService {

    private static final Set<String> VALID_COMPLETION_POLICIES =
            Set.of("NEVER_COMPLETE", "MANUAL_ONLY", "EVIDENCE_ALLOWED");

    private final SkillNodeRepository skillNodeRepository;
    private final NodeTypeRepository nodeTypeRepository;
    private final CareerRoleRepository careerRoleRepository;
    private final StudentProgressRepository studentProgressRepository;
    private final RoadmapNodeLayoutRepository layoutRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    @Override
    public List<RoadmapNodeResponse> getCareerNodes(UUID careerId) {
        log.info("RoadmapEditorServiceImpl: Editor node list request received. careerId: {}", careerId);
        requireCareer(careerId);

        List<SkillNode> nodes = skillNodeRepository.findByCareerRole_CareerId(careerId);
        Map<UUID, RoadmapNodeLayout> layouts = loadLayouts(nodes.stream().map(SkillNode::getNodeId).toList());
        return nodes.stream()
                .map(node -> toEditorDto(node, layouts.get(node.getNodeId())))
                .toList();
    }

    @Transactional
    @Override
    public void saveNodePositions(SaveNodePositionsRequest request) {
        log.info("RoadmapEditorServiceImpl: Saving {} node layout(s)", request.getPositions().size());
        UUID editorId = currentUserId();

        for (NodePosition position : request.getPositions()) {
            SkillNode node = requireNode(position.getNodeId());
            RoadmapNodeLayout layout = layoutRepository.findByNodeId(node.getNodeId()).orElse(null);

            if (layout == null) {
                layout = RoadmapNodeLayout.builder().nodeId(node.getNodeId()).layoutVersion(0).build();
            } else if (position.getLayoutVersion() != null
                    && !position.getLayoutVersion().equals(layout.getLayoutVersion())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This roadmap layout was changed by someone else. Reload before saving.");
            }

            layout.setPositionX(position.getPositionX());
            layout.setPositionY(position.getPositionY());
            if (position.getLane() != null) {
                layout.setLane(position.getLane());
            }
            if (position.getDisplayOrder() != null) {
                layout.setDisplayOrder(position.getDisplayOrder());
            }
            layout.setLayoutVersion(layout.getLayoutVersion() + 1);
            layout.setEditedBy(editorId);
            layout.setUpdatedAt(LocalDateTime.now());
            layoutRepository.save(layout);
        }
    }

    @Transactional
    @Override
    public RoadmapNodeResponse createNode(UUID careerId, UpsertRoadmapNodeRequest request) {
        log.info("RoadmapEditorServiceImpl: Create node request received. careerId: {}", careerId);
        CareerRole career = requireCareer(careerId);

        SkillNode node = SkillNode.builder().careerRole(career).build();
        applyLogic(node, request, careerId);
        node = skillNodeRepository.save(node);

        RoadmapNodeLayout layout = applyLayout(node.getNodeId(), request);
        log.info("RoadmapEditorServiceImpl: Node '{}' created ({})", node.getNodeName(), node.getNodeId());
        return toEditorDto(node, layout);
    }

    @Transactional
    @Override
    public RoadmapNodeResponse updateNode(UUID nodeId, UpsertRoadmapNodeRequest request) {
        log.info("RoadmapEditorServiceImpl: Update node request received. nodeId: {}", nodeId);
        SkillNode node = requireNode(nodeId);
        applyLogic(node, request, node.getCareerRole().getCareerId());
        node = skillNodeRepository.save(node);

        RoadmapNodeLayout layout = applyLayout(node.getNodeId(), request);
        log.info("RoadmapEditorServiceImpl: Node '{}' updated", node.getNodeName());
        return toEditorDto(node, layout);
    }

    @Transactional
    @Override
    public void deleteNode(UUID nodeId) {
        log.info("RoadmapEditorServiceImpl: Delete node request received. nodeId: {}", nodeId);
        SkillNode node = requireNode(nodeId);

        if (studentProgressRepository.existsBySkillNode_NodeId(nodeId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete: students already have progress on this node");
        }
        if (skillNodeRepository.existsByParentNode_NodeId(nodeId)
                || skillNodeRepository.existsByPreviousNode_NodeId(nodeId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete: other nodes reference this node as parent or previous. Re-link them first");
        }

        // The layout row is removed by ON DELETE CASCADE.
        skillNodeRepository.delete(node);
        log.info("RoadmapEditorServiceImpl: Node '{}' deleted", node.getNodeName());
    }

    // ------------------------------------------------------------------
    // Logic (skill_nodes) helpers
    // ------------------------------------------------------------------

    private void applyLogic(SkillNode node, UpsertRoadmapNodeRequest request, UUID careerId) {
        node.setNodeName(request.getNodeName().trim());
        node.setDescription(request.getDescription());
        node.setCompletionPolicy(parseCompletionPolicy(request.getCompletionPolicy()));
        node.setRequiredProficiency(request.getRequiredProficiency());
        node.setParentNode(resolveReference(request.getParentNodeId(), node, careerId, "parent"));
        node.setPreviousNode(resolveReference(request.getPreviousNodeId(), node, careerId, "previous"));
        node.setResource(toResourceJson(request.getResources()));
        applyNodeType(node, request);
    }

    /** Each node owns its NodeType row; update it in place or create one. */
    private void applyNodeType(SkillNode node, UpsertRoadmapNodeRequest request) {
        StageType stage = parseStage(request.getStage());
        NodeType type = node.getType();
        if (type == null) {
            type = NodeType.builder()
                    .stage(stage != null ? stage : StageType.FOUNDATION)
                    .unlockKeyRequired(false)
                    .build();
        } else if (stage != null) {
            type.setStage(stage);
        }
        type.setWeight(request.getWeight());
        node.setType(nodeTypeRepository.save(type));
    }

    private SkillNode resolveReference(UUID referencedNodeId, SkillNode node, UUID careerId, String label) {
        if (referencedNodeId == null) {
            return null;
        }
        if (node.getNodeId() != null && referencedNodeId.equals(node.getNodeId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A node cannot reference itself as " + label);
        }
        SkillNode referenced = requireNode(referencedNodeId);
        if (!careerId.equals(referenced.getCareerRole().getCareerId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The " + label + " node belongs to a different career roadmap");
        }
        assertNoAncestorCycle(node, referenced, label);
        return referenced;
    }

    /** Walks the candidate's parent/previous chain to reject cycles back to the edited node. */
    private void assertNoAncestorCycle(SkillNode node, SkillNode candidate, String label) {
        if (node.getNodeId() == null) {
            return;
        }
        Set<UUID> visited = new HashSet<>();
        SkillNode cursor = candidate;
        while (cursor != null && visited.add(cursor.getNodeId())) {
            if (node.getNodeId().equals(cursor.getNodeId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Linking this " + label + " node would create a cycle in the roadmap");
            }
            cursor = cursor.getParentNode() != null ? cursor.getParentNode() : cursor.getPreviousNode();
        }
    }

    // ------------------------------------------------------------------
    // Layout (roadmap_node_layouts) helpers
    // ------------------------------------------------------------------

    /** Writes optional placement fields from an upsert; returns the saved (or existing) layout. */
    private RoadmapNodeLayout applyLayout(UUID nodeId, UpsertRoadmapNodeRequest request) {
        boolean hasLayout = request.getPositionX() != null || request.getPositionY() != null
                || request.getLane() != null || request.getDisplayOrder() != null;
        RoadmapNodeLayout existing = layoutRepository.findByNodeId(nodeId).orElse(null);
        if (!hasLayout) {
            return existing;
        }

        RoadmapNodeLayout layout = existing != null
                ? existing
                : RoadmapNodeLayout.builder().nodeId(nodeId).layoutVersion(0).build();
        layout.setPositionX(request.getPositionX());
        layout.setPositionY(request.getPositionY());
        layout.setLane(request.getLane());
        layout.setDisplayOrder(request.getDisplayOrder());
        layout.setLayoutVersion(layout.getLayoutVersion() + 1);
        layout.setEditedBy(currentUserId());
        layout.setUpdatedAt(LocalDateTime.now());
        return layoutRepository.save(layout);
    }

    private Map<UUID, RoadmapNodeLayout> loadLayouts(List<UUID> nodeIds) {
        Map<UUID, RoadmapNodeLayout> byNodeId = new HashMap<>();
        if (nodeIds.isEmpty()) {
            return byNodeId;
        }
        for (RoadmapNodeLayout layout : layoutRepository.findByNodeIdIn(nodeIds)) {
            byNodeId.put(layout.getNodeId(), layout);
        }
        return byNodeId;
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    private StageType parseStage(String stage) {
        if (stage == null || stage.isBlank()) {
            return null;
        }
        try {
            return StageType.valueOf(stage.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid stage '" + stage + "'. Valid stages: FOUNDATION, CORE, PRACTICAL, ADVANCED, JOB_READY");
        }
    }

    private String parseCompletionPolicy(String policy) {
        if (policy == null || policy.isBlank()) {
            return null;
        }
        String normalized = policy.trim().toUpperCase();
        if (!VALID_COMPLETION_POLICIES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid completionPolicy '" + policy + "'. Valid values: NEVER_COMPLETE, MANUAL_ONLY, EVIDENCE_ALLOWED");
        }
        return normalized;
    }

    private ArrayNode toResourceJson(List<String> resources) {
        ArrayNode links = objectMapper.createArrayNode();
        if (resources != null) {
            resources.stream()
                    .filter(link -> link != null && !link.isBlank())
                    .forEach(link -> links.add(link.trim()));
        }
        return links;
    }

    private RoadmapNodeResponse toEditorDto(SkillNode node, RoadmapNodeLayout layout) {
        return RoadmapNodeResponse.builder()
                .nodeId(node.getNodeId())
                .nodeName(node.getNodeName())
                .parentNode(node.getParentNode() != null ? node.getParentNode().getNodeId().toString() : null)
                .previousNode(node.getPreviousNode() != null ? node.getPreviousNode().getNodeId().toString() : null)
                .nodeLevel(node.getNodeLevel())
                .stage(node.getType() != null && node.getType().getStage() != null
                        ? node.getType().getStage().name() : null)
                .completionPolicy(node.getCompletionPolicy())
                .weight(node.getType() != null ? node.getType().getWeight() : null)
                .requiredProficiency(node.getRequiredProficiency())
                .positionX(layout != null ? layout.getPositionX() : null)
                .positionY(layout != null ? layout.getPositionY() : null)
                .lane(layout != null ? layout.getLane() : null)
                .displayOrder(layout != null ? layout.getDisplayOrder() : null)
                .layoutVersion(layout != null ? layout.getLayoutVersion() : null)
                .description(node.getDescription())
                .resource(node.getResource())
                .build();
    }

    private CareerRole requireCareer(UUID careerId) {
        CareerRole career = careerRoleRepository.findByCareerId(careerId);
        if (career == null) {
            throw new ResourceNotFoundException("Career not found");
        }
        return career;
    }

    private SkillNode requireNode(UUID nodeId) {
        return skillNodeRepository.findById(nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Node not found"));
    }

    private UUID currentUserId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email);
        return user != null ? user.getUserId() : null;
    }
}
