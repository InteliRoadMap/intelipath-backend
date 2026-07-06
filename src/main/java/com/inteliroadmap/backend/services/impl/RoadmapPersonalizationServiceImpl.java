package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.components.RoadmapProgressCalculator;
import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapRecommendationDecisionResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapRecommendationResponse;
import com.inteliroadmap.backend.domain.entity.CareerRequiredSkill;
import com.inteliroadmap.backend.domain.entity.RoadmapRecommendation;
import com.inteliroadmap.backend.domain.entity.RoadmapRecommendationItem;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.entity.SkillNode;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.StudentProgress;
import com.inteliroadmap.backend.domain.entity.StudentSkill;
import com.inteliroadmap.backend.domain.entity.StudentSkillEvidence;
import com.inteliroadmap.backend.domain.enums.EvidenceStatus;
import com.inteliroadmap.backend.domain.enums.ImportanceLevel;
import com.inteliroadmap.backend.domain.enums.RecommendationAction;
import com.inteliroadmap.backend.domain.enums.RecommendationStatus;
import com.inteliroadmap.backend.domain.enums.RecommendationType;
import com.inteliroadmap.backend.domain.enums.RoadmapStepStatus;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.mappers.RoadmapRecommendationMapper;
import com.inteliroadmap.backend.repositories.CareerRequiredSkillRepository;
import com.inteliroadmap.backend.repositories.RoadmapRecommendationItemRepository;
import com.inteliroadmap.backend.repositories.RoadmapRecommendationRepository;
import com.inteliroadmap.backend.repositories.SkillNodeRepository;
import com.inteliroadmap.backend.repositories.StudentProgressRepository;
import com.inteliroadmap.backend.repositories.StudentSkillEvidenceRepository;
import com.inteliroadmap.backend.repositories.StudentSkillRepository;
import com.inteliroadmap.backend.services.AuthenticatedStudentService;
import com.inteliroadmap.backend.services.RoadmapPersonalizationService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of {@link RoadmapPersonalizationService}.
 *
 * Recommendation sources, in this phase:
 * - skills already present in student_skills (confidence {@link #PROFILE_SKILL_CONFIDENCE}),
 * - PENDING/ACCEPTED rows in student_skill_evidence with confidence of at
 *   least {@link #MIN_EVIDENCE_CONFIDENCE}, matched to career nodes by
 *   node_id first and by skill name second.
 *
 * Evidence ingestion from the Python AI service is intentionally out of scope
 * here; it only needs to insert student_skill_evidence rows (user_id set,
 * student_skill_id left NULL) for this service to pick them up.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoadmapPersonalizationServiceImpl implements RoadmapPersonalizationService {

    private static final BigDecimal MIN_EVIDENCE_CONFIDENCE = new BigDecimal("0.70");
    private static final BigDecimal PROFILE_SKILL_CONFIDENCE = new BigDecimal("0.80");

    /**
     * Confidence a skill's evidence must clear to fast-track a node, scaled by how
     * important that skill is to the career. HIGH-importance (foundational) skills
     * demand stronger proof before we let the student skip them; LOW-importance
     * skills are cheaper to accept. Applied as a floor on top of the node's own
     * requiredProficiency, so we always take the stricter of the two.
     */
    private static final BigDecimal HIGH_IMPORTANCE_CONFIDENCE = new BigDecimal("0.85");
    private static final BigDecimal AVG_IMPORTANCE_CONFIDENCE = new BigDecimal("0.70");
    private static final BigDecimal LOW_IMPORTANCE_CONFIDENCE = new BigDecimal("0.60");

    /**
     * Only EVIDENCE_ALLOWED nodes may be auto-suggested. NEVER_COMPLETE nodes are
     * group headers, and MANUAL_ONLY nodes must be ticked by the student directly.
     */
    private static final String EVIDENCE_ALLOWED_POLICY = "EVIDENCE_ALLOWED";

    private final AuthenticatedStudentService authenticatedStudentService;
    private final RoadmapRecommendationRepository recommendationRepository;
    private final RoadmapRecommendationItemRepository recommendationItemRepository;
    private final StudentSkillEvidenceRepository evidenceRepository;
    private final StudentProgressRepository studentProgressRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final SkillNodeRepository skillNodeRepository;
    private final CareerRequiredSkillRepository careerRequiredSkillRepository;
    private final RoadmapRecommendationMapper recommendationMapper;
    private final RoadmapProgressCalculator progressCalculator;

    @Transactional
    @Override
    public List<RoadmapRecommendationResponse> generateRecommendationsForCurrentStudent() {
        log.info("RoadmapPersonalizationServiceImpl: Generate recommendations request received");

        Student student = authenticatedStudentService.getRequiredStudent();
        UUID careerId = requireSelectedCareer(student);

        List<SkillNode> careerNodes = skillNodeRepository.findByCareerRole_CareerId(careerId);
        if (careerNodes.isEmpty()) {
            log.info("RoadmapPersonalizationServiceImpl: Career {} has no roadmap nodes, nothing to recommend", careerId);
            return List.of();
        }

        Set<UUID> excludedNodeIds = new HashSet<>();
        excludedNodeIds.addAll(findCompletedNodeIds(student.getUserId()));
        excludedNodeIds.addAll(findNodeIdsInPendingRecommendations(student.getUserId()));

        Map<UUID, ImportanceLevel> importanceBySkillId = loadImportanceBySkillId(careerId);
        Map<UUID, NodeCandidate> candidates = collectCandidates(student, careerNodes, excludedNodeIds, importanceBySkillId);
        if (candidates.isEmpty()) {
            log.info("RoadmapPersonalizationServiceImpl: No new skippable nodes found for user {}", student.getUserId());
            return List.of();
        }

        RoadmapRecommendation recommendation = saveRecommendation(student, careerId, candidates.values());
        List<RoadmapRecommendationItem> items = saveRecommendationItems(recommendation, candidates.values());

        log.info("RoadmapPersonalizationServiceImpl: Created recommendation {} with {} item(s) for user {}",
                recommendation.getRecommendationId(), items.size(), student.getUserId());
        return List.of(recommendationMapper.toRecommendationResponse(
                recommendation, items, nodeNamesById(careerNodes)));
    }

    @Transactional
    @Override
    public List<RoadmapRecommendationResponse> getPendingRecommendationsForCurrentStudent() {
        log.info("RoadmapPersonalizationServiceImpl: Pending recommendations request received");

        Student student = authenticatedStudentService.getRequiredStudent();
        List<RoadmapRecommendation> recommendations = recommendationRepository
                .findByUserIdAndStatusOrderByCreatedAtDesc(student.getUserId(), RecommendationStatus.PENDING);
        if (recommendations.isEmpty()) {
            return List.of();
        }

        List<UUID> recommendationIds = recommendations.stream()
                .map(RoadmapRecommendation::getRecommendationId)
                .toList();
        Map<UUID, List<RoadmapRecommendationItem>> itemsByRecommendationId = new HashMap<>();
        for (RoadmapRecommendationItem item : recommendationItemRepository.findByRecommendationIdIn(recommendationIds)) {
            itemsByRecommendationId.computeIfAbsent(item.getRecommendationId(), key -> new ArrayList<>()).add(item);
        }

        Map<UUID, String> nodeNames = nodeNamesForItems(itemsByRecommendationId.values());
        return recommendations.stream()
                .map(recommendation -> recommendationMapper.toRecommendationResponse(
                        recommendation,
                        itemsByRecommendationId.getOrDefault(recommendation.getRecommendationId(), List.of()),
                        nodeNames))
                .toList();
    }

    @Transactional
    @Override
    public RoadmapRecommendationDecisionResponse acceptRecommendation(UUID recommendationId) {
        log.info("RoadmapPersonalizationServiceImpl: Accept recommendation request received. recommendationId: {}", recommendationId);

        Student student = authenticatedStudentService.getRequiredStudent();
        RoadmapRecommendation recommendation = getOwnedPendingRecommendation(recommendationId, student);
        List<RoadmapRecommendationItem> items = recommendationItemRepository.findByRecommendationId(recommendationId);

        List<SkillNode> completedNodes = applyMarkCompleteItems(student, items);
        syncCompletedSkillsToProfile(student, completedNodes);
        linkAndAcceptEvidence(student, items, completedNodes);

        recommendation.setStatus(RecommendationStatus.ACCEPTED);
        recommendation.setDecidedAt(LocalDateTime.now());
        recommendationRepository.save(recommendation);

        int progress = calculateCurrentProgress(student);
        log.info("RoadmapPersonalizationServiceImpl: Recommendation {} accepted, {} node(s) completed, roadmap progress now {}%",
                recommendationId, completedNodes.size(), progress);

        return RoadmapRecommendationDecisionResponse.builder()
                .recommendationId(recommendationId)
                .status(RecommendationStatus.ACCEPTED)
                .decidedAt(recommendation.getDecidedAt())
                .roadmapProgress(progress)
                .build();
    }

    @Transactional
    @Override
    public RoadmapRecommendationDecisionResponse rejectRecommendation(UUID recommendationId) {
        log.info("RoadmapPersonalizationServiceImpl: Reject recommendation request received. recommendationId: {}", recommendationId);

        Student student = authenticatedStudentService.getRequiredStudent();
        RoadmapRecommendation recommendation = getOwnedPendingRecommendation(recommendationId, student);

        recommendation.setStatus(RecommendationStatus.REJECTED);
        recommendation.setDecidedAt(LocalDateTime.now());
        recommendationRepository.save(recommendation);

        log.info("RoadmapPersonalizationServiceImpl: Recommendation {} rejected", recommendationId);
        return RoadmapRecommendationDecisionResponse.builder()
                .recommendationId(recommendationId)
                .status(RecommendationStatus.REJECTED)
                .decidedAt(recommendation.getDecidedAt())
                .build();
    }

    // ------------------------------------------------------------------
    // Generation helpers
    // ------------------------------------------------------------------

    /** A roadmap node the student can likely skip, with the best supporting confidence. */
    private record NodeCandidate(SkillNode node, BigDecimal confidence, String reason, List<UUID> evidenceIds) {
    }

    /** Maps each skill required by the career to its importance level, for the confidence gate. */
    private Map<UUID, ImportanceLevel> loadImportanceBySkillId(UUID careerId) {
        Map<UUID, ImportanceLevel> importanceBySkillId = new HashMap<>();
        for (CareerRequiredSkill required : careerRequiredSkillRepository.findByCareerRole_CareerId(careerId)) {
            if (required.getSkill() != null && required.getImportanceLevel() != null) {
                importanceBySkillId.put(required.getSkill().getSkillId(), required.getImportanceLevel());
            }
        }
        return importanceBySkillId;
    }

    private Map<UUID, NodeCandidate> collectCandidates(Student student, List<SkillNode> careerNodes,
                                                       Set<UUID> excludedNodeIds,
                                                       Map<UUID, ImportanceLevel> importanceBySkillId) {
        Map<UUID, NodeCandidate> candidates = new LinkedHashMap<>();

        Map<UUID, List<SkillNode>> nodesBySkillId = new HashMap<>();
        Map<String, List<SkillNode>> nodesBySkillName = new HashMap<>();
        Map<UUID, SkillNode> nodesById = new HashMap<>();
        for (SkillNode node : careerNodes) {
            nodesById.put(node.getNodeId(), node);
            if (node.getSkill() != null) {
                nodesBySkillId.computeIfAbsent(node.getSkill().getSkillId(), key -> new ArrayList<>()).add(node);
                if (node.getSkill().getSkillName() != null) {
                    nodesBySkillName
                            .computeIfAbsent(node.getSkill().getSkillName().toLowerCase(), key -> new ArrayList<>())
                            .add(node);
                }
            }
        }

        // Source 1: skills the student already holds in their profile.
        for (StudentSkill studentSkill : studentSkillRepository.findByStudent_UserId(student.getUserId())) {
            if (studentSkill.getSkill() == null) {
                continue;
            }
            String reason = "You already have '" + studentSkill.getSkill().getSkillName() + "' in your skill profile";
            for (SkillNode node : nodesBySkillId.getOrDefault(studentSkill.getSkill().getSkillId(), List.of())) {
                offerCandidate(candidates, excludedNodeIds, node, PROFILE_SKILL_CONFIDENCE, reason, null, importanceBySkillId);
            }
        }

        // Source 2: AI/portfolio/transcript evidence. Per-node proficiency
        // thresholds are enforced in offerCandidate.
        List<StudentSkillEvidence> evidences = evidenceRepository.findByUserIdAndStatusIn(
                student.getUserId(), List.of(EvidenceStatus.PENDING, EvidenceStatus.ACCEPTED));
        for (StudentSkillEvidence evidence : evidences) {
            BigDecimal confidence = evidence.getConfidence();
            if (confidence == null) {
                continue;
            }
            String reason = buildEvidenceReason(evidence);
            if (evidence.getNodeId() != null && nodesById.containsKey(evidence.getNodeId())) {
                offerCandidate(candidates, excludedNodeIds,
                        nodesById.get(evidence.getNodeId()), confidence, reason, evidence.getEvidenceId(), importanceBySkillId);
            } else if (evidence.getSkillName() != null) {
                for (SkillNode node : nodesBySkillName.getOrDefault(evidence.getSkillName().toLowerCase(), List.of())) {
                    offerCandidate(candidates, excludedNodeIds, node, confidence, reason, evidence.getEvidenceId(), importanceBySkillId);
                }
            }
        }

        return candidates;
    }

    /** Adds or merges a candidate, keeping the highest confidence and every supporting evidence id. */
    private void offerCandidate(Map<UUID, NodeCandidate> candidates, Set<UUID> excludedNodeIds,
                                SkillNode node, BigDecimal confidence, String reason, UUID evidenceId,
                                Map<UUID, ImportanceLevel> importanceBySkillId) {
        if (excludedNodeIds.contains(node.getNodeId())) {
            return;
        }
        if (!EVIDENCE_ALLOWED_POLICY.equalsIgnoreCase(node.getCompletionPolicy())) {
            return;
        }
        if (!meetsNodeProficiency(node, confidence, importanceBySkillId)) {
            return;
        }

        NodeCandidate existing = candidates.get(node.getNodeId());
        if (existing == null) {
            List<UUID> evidenceIds = new ArrayList<>();
            if (evidenceId != null) {
                evidenceIds.add(evidenceId);
            }
            candidates.put(node.getNodeId(), new NodeCandidate(node, confidence, reason, evidenceIds));
            return;
        }

        if (evidenceId != null && !existing.evidenceIds().contains(evidenceId)) {
            existing.evidenceIds().add(evidenceId);
        }
        if (confidence.compareTo(existing.confidence()) > 0) {
            candidates.put(node.getNodeId(),
                    new NodeCandidate(node, confidence, reason, existing.evidenceIds()));
        }
    }

    /**
     * The node's requiredProficiency (0-100) is the confidence bar for suggesting
     * it; nodes without one fall back to the default threshold. On top of that we
     * apply an importance floor: HIGH-importance skills need stronger evidence
     * before we let the student skip them. The stricter of the two wins.
     */
    private boolean meetsNodeProficiency(SkillNode node, BigDecimal confidence,
                                         Map<UUID, ImportanceLevel> importanceBySkillId) {
        if (confidence == null) {
            return false;
        }
        BigDecimal threshold = node.getRequiredProficiency() != null && node.getRequiredProficiency() > 0
                ? BigDecimal.valueOf(node.getRequiredProficiency()).movePointLeft(2)
                : MIN_EVIDENCE_CONFIDENCE;
        threshold = threshold.max(importanceFloor(node, importanceBySkillId));
        return confidence.compareTo(threshold) >= 0;
    }

    /** The minimum confidence dictated by how important the node's skill is to the career. */
    private BigDecimal importanceFloor(SkillNode node, Map<UUID, ImportanceLevel> importanceBySkillId) {
        if (node.getSkill() == null) {
            return AVG_IMPORTANCE_CONFIDENCE;
        }
        ImportanceLevel importance = importanceBySkillId.get(node.getSkill().getSkillId());
        if (importance == null) {
            return AVG_IMPORTANCE_CONFIDENCE;
        }
        return switch (importance) {
            case HIGH -> HIGH_IMPORTANCE_CONFIDENCE;
            case AVG -> AVG_IMPORTANCE_CONFIDENCE;
            case LOW -> LOW_IMPORTANCE_CONFIDENCE;
        };
    }

    private String buildEvidenceReason(StudentSkillEvidence evidence) {
        String skillLabel = evidence.getSkillName() != null ? "'" + evidence.getSkillName() + "'" : "this skill";
        return "Detected " + skillLabel + " from your "
                + evidence.getSourceType().name().toLowerCase().replace('_', ' ');
    }

    private RoadmapRecommendation saveRecommendation(Student student, UUID careerId, Iterable<NodeCandidate> candidates) {
        return recommendationRepository.save(RoadmapRecommendation.builder()
                .userId(student.getUserId())
                .currentCareerId(careerId)
                .recommendCareerId(careerId)
                .recommendationType(RecommendationType.SKIP_KNOWN_SKILLS)
                .title("Skip skills you already know")
                .summary("Based on your skill profile and detected evidence, you can mark "
                        + countOf(candidates) + " roadmap node(s) as completed.")
                .reason("Your existing skills and evidence cover these roadmap nodes. "
                        + "Accepting will mark them completed and update your progress.")
                .confidence(averageConfidence(candidates))
                .status(RecommendationStatus.PENDING)
                .build());
    }

    private List<RoadmapRecommendationItem> saveRecommendationItems(
            RoadmapRecommendation recommendation, Iterable<NodeCandidate> candidates) {
        List<RoadmapRecommendationItem> items = new ArrayList<>();
        for (NodeCandidate candidate : candidates) {
            items.add(RoadmapRecommendationItem.builder()
                    .recommendationId(recommendation.getRecommendationId())
                    .nodeId(candidate.node().getNodeId())
                    .action(RecommendationAction.MARK_COMPLETE)
                    .reason(candidate.reason())
                    .confidence(candidate.confidence())
                    .evidenceIds(candidate.evidenceIds().isEmpty() ? null : candidate.evidenceIds())
                    .build());
        }
        return recommendationItemRepository.saveAll(items);
    }

    // ------------------------------------------------------------------
    // Accept helpers
    // ------------------------------------------------------------------

    /** Marks every MARK_COMPLETE item's node as completed and returns the affected nodes. */
    private List<SkillNode> applyMarkCompleteItems(Student student, List<RoadmapRecommendationItem> items) {
        List<SkillNode> completedNodes = new ArrayList<>();
        for (RoadmapRecommendationItem item : items) {
            if (item.getAction() != RecommendationAction.MARK_COMPLETE) {
                continue;
            }
            SkillNode node = skillNodeRepository.findById(item.getNodeId()).orElse(null);
            if (node == null) {
                log.warn("RoadmapPersonalizationServiceImpl: Skipping item {}, node {} no longer exists",
                        item.getRecItemId(), item.getNodeId());
                continue;
            }
            upsertCompletedProgress(student, node);
            completedNodes.add(node);
        }
        return completedNodes;
    }

    private void upsertCompletedProgress(Student student, SkillNode node) {
        StudentProgress progress = studentProgressRepository
                .findByStudent_UserIdAndSkillNode_NodeId(student.getUserId(), node.getNodeId())
                .orElseGet(() -> StudentProgress.builder()
                        .student(Student.builder().userId(student.getUserId()).build())
                        .skillNode(node)
                        .build());

        progress.setStatus(RoadmapStepStatus.COMPLETED);
        if (progress.getCompletedAt() == null) {
            progress.setCompletedAt(LocalDateTime.now());
        }
        studentProgressRepository.save(progress);
    }

    /**
     * For each skill touched by the accepted nodes: when every node of that
     * skill in the current career is COMPLETED, add the skill to
     * student_skills (if not already there).
     */
    private void syncCompletedSkillsToProfile(Student student, List<SkillNode> completedNodes) {
        UUID careerId = student.getCareerRole().getCareerId();
        Map<UUID, Skill> touchedSkills = new HashMap<>();
        for (SkillNode node : completedNodes) {
            if (node.getSkill() != null) {
                touchedSkills.putIfAbsent(node.getSkill().getSkillId(), node.getSkill());
            }
        }

        for (Skill skill : touchedSkills.values()) {
            if (studentSkillRepository.existsByStudent_UserIdAndSkill_SkillId(student.getUserId(), skill.getSkillId())) {
                continue;
            }
            if (allSkillNodesCompleted(student.getUserId(), skill.getSkillId(), careerId)) {
                studentSkillRepository.save(StudentSkill.builder()
                        .student(Student.builder().userId(student.getUserId()).build())
                        .skill(skill)
                        .build());
                log.info("RoadmapPersonalizationServiceImpl: Skill '{}' synced to profile of user {}",
                        skill.getSkillName(), student.getUserId());
            }
        }
    }

    private boolean allSkillNodesCompleted(UUID userId, UUID skillId, UUID careerId) {
        for (SkillNode node : skillNodeRepository.findBySkill_SkillIdAndCareerRole_CareerId(skillId, careerId)) {
            StudentProgress progress = studentProgressRepository
                    .findByStudent_UserIdAndSkillNode_NodeId(userId, node.getNodeId())
                    .orElse(null);
            if (progress == null || progress.getStatus() != RoadmapStepStatus.COMPLETED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Accepts the evidence rows referenced by the items and, where the skill
     * has just landed in student_skills, back-fills evidence.student_skill_id.
     */
    private void linkAndAcceptEvidence(Student student, List<RoadmapRecommendationItem> items, List<SkillNode> completedNodes) {
        Set<UUID> evidenceIds = new HashSet<>();
        for (RoadmapRecommendationItem item : items) {
            if (item.getEvidenceIds() != null) {
                evidenceIds.addAll(item.getEvidenceIds());
            }
        }
        if (evidenceIds.isEmpty()) {
            return;
        }

        Map<UUID, UUID> nodeIdToSkillId = new HashMap<>();
        for (SkillNode node : completedNodes) {
            if (node.getSkill() != null) {
                nodeIdToSkillId.put(node.getNodeId(), node.getSkill().getSkillId());
            }
        }
        Map<UUID, UUID> skillIdToStudentSkillId = new HashMap<>();
        Map<String, UUID> skillNameToStudentSkillId = new HashMap<>();
        for (StudentSkill studentSkill : studentSkillRepository.findByStudent_UserId(student.getUserId())) {
            if (studentSkill.getSkill() != null) {
                skillIdToStudentSkillId.put(studentSkill.getSkill().getSkillId(), studentSkill.getStudentSkillId());
                if (studentSkill.getSkill().getSkillName() != null) {
                    skillNameToStudentSkillId.put(
                            studentSkill.getSkill().getSkillName().toLowerCase(), studentSkill.getStudentSkillId());
                }
            }
        }

        for (StudentSkillEvidence evidence : evidenceRepository.findAllById(evidenceIds)) {
            evidence.setStatus(EvidenceStatus.ACCEPTED);
            if (evidence.getStudentSkillId() == null) {
                evidence.setStudentSkillId(resolveStudentSkillId(
                        evidence, nodeIdToSkillId, skillIdToStudentSkillId, skillNameToStudentSkillId));
            }
            evidenceRepository.save(evidence);
        }
    }

    private UUID resolveStudentSkillId(StudentSkillEvidence evidence,
                                       Map<UUID, UUID> nodeIdToSkillId,
                                       Map<UUID, UUID> skillIdToStudentSkillId,
                                       Map<String, UUID> skillNameToStudentSkillId) {
        if (evidence.getNodeId() != null && nodeIdToSkillId.containsKey(evidence.getNodeId())) {
            return skillIdToStudentSkillId.get(nodeIdToSkillId.get(evidence.getNodeId()));
        }
        if (evidence.getSkillName() != null) {
            return skillNameToStudentSkillId.get(evidence.getSkillName().toLowerCase());
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    private RoadmapRecommendation getOwnedPendingRecommendation(UUID recommendationId, Student student) {
        RoadmapRecommendation recommendation = recommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new ResourceNotFoundException("Recommendation not found"));

        if (!student.getUserId().equals(recommendation.getUserId())) {
            log.warn("RoadmapPersonalizationServiceImpl: User {} tried to decide recommendation {} owned by {}",
                    student.getUserId(), recommendationId, recommendation.getUserId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Recommendation does not belong to the current student");
        }
        if (recommendation.getStatus() != RecommendationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Recommendation has already been decided");
        }
        return recommendation;
    }

    private UUID requireSelectedCareer(Student student) {
        if (student.getCareerRole() == null || student.getCareerRole().getCareerId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Student has not selected a career path");
        }
        return student.getCareerRole().getCareerId();
    }

    private Set<UUID> findCompletedNodeIds(UUID userId) {
        Set<UUID> completedNodeIds = new HashSet<>();
        for (StudentProgress progress : studentProgressRepository.findByStudent_UserId(userId)) {
            if (progress.getStatus() == RoadmapStepStatus.COMPLETED) {
                completedNodeIds.add(progress.getSkillNode().getNodeId());
            }
        }
        return completedNodeIds;
    }

    /** Node ids already proposed as MARK_COMPLETE in another PENDING recommendation (duplicate guard). */
    private Set<UUID> findNodeIdsInPendingRecommendations(UUID userId) {
        List<UUID> pendingIds = recommendationRepository
                .findByUserIdAndStatusOrderByCreatedAtDesc(userId, RecommendationStatus.PENDING)
                .stream()
                .map(RoadmapRecommendation::getRecommendationId)
                .toList();
        if (pendingIds.isEmpty()) {
            return Set.of();
        }

        Set<UUID> nodeIds = new HashSet<>();
        for (RoadmapRecommendationItem item : recommendationItemRepository.findByRecommendationIdIn(pendingIds)) {
            if (item.getAction() == RecommendationAction.MARK_COMPLETE) {
                nodeIds.add(item.getNodeId());
            }
        }
        return nodeIds;
    }

    private int calculateCurrentProgress(Student student) {
        List<SkillNode> careerNodes = skillNodeRepository
                .findByCareerRole_CareerId(student.getCareerRole().getCareerId());
        List<StudentProgress> progresses = studentProgressRepository.findByStudent_UserId(student.getUserId());
        return progressCalculator.calculateProgress(careerNodes, progresses);
    }

    private Map<UUID, String> nodeNamesById(List<SkillNode> nodes) {
        Map<UUID, String> names = new HashMap<>();
        for (SkillNode node : nodes) {
            names.put(node.getNodeId(), node.getNodeName());
        }
        return names;
    }

    private Map<UUID, String> nodeNamesForItems(Iterable<List<RoadmapRecommendationItem>> itemGroups) {
        Set<UUID> nodeIds = new HashSet<>();
        for (List<RoadmapRecommendationItem> group : itemGroups) {
            for (RoadmapRecommendationItem item : group) {
                nodeIds.add(item.getNodeId());
            }
        }
        return nodeNamesById(skillNodeRepository.findAllById(nodeIds));
    }

    private int countOf(Iterable<NodeCandidate> candidates) {
        int count = 0;
        for (NodeCandidate ignored : candidates) {
            count++;
        }
        return count;
    }

    private BigDecimal averageConfidence(Iterable<NodeCandidate> candidates) {
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (NodeCandidate candidate : candidates) {
            sum = sum.add(candidate.confidence());
            count++;
        }
        if (count == 0) {
            return null;
        }
        return sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }
}
