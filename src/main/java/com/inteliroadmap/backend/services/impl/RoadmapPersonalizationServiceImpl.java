package com.inteliroadmap.backend.services.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.inteliroadmap.backend.components.AbilityFloorPropagator;
import com.inteliroadmap.backend.components.RoadmapProgressCalculator;
import com.inteliroadmap.backend.components.RoadmapSelectionResolver;
import com.inteliroadmap.backend.components.SelectionView;
import com.inteliroadmap.backend.components.SkillProficiencyPromoter;
import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapRecommendationDecisionResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapRecommendationResponse;
import com.inteliroadmap.backend.domain.entity.CareerRequiredSkill;
import com.inteliroadmap.backend.domain.entity.RoadmapRecommendation;
import com.inteliroadmap.backend.domain.entity.RoadmapRecommendationItem;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.entity.SkillNode;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.StudentNodeSelection;
import com.inteliroadmap.backend.domain.entity.StudentProgress;
import com.inteliroadmap.backend.domain.entity.StudentSkill;
import com.inteliroadmap.backend.domain.entity.StudentSkillEvidence;
import com.inteliroadmap.backend.domain.enums.EvidenceStatus;
import com.inteliroadmap.backend.domain.enums.EvidenceType;
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
import com.inteliroadmap.backend.repositories.StudentNodeSelectionRepository;
import com.inteliroadmap.backend.repositories.StudentProgressRepository;
import com.inteliroadmap.backend.repositories.StudentSkillEvidenceRepository;
import com.inteliroadmap.backend.repositories.StudentSkillRepository;
import com.inteliroadmap.backend.services.AuthenticatedStudentService;
import com.inteliroadmap.backend.services.RoadmapPersonalizationService;
import com.inteliroadmap.backend.services.StudentLevelService;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Locale;
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

    /**
     * A student_skills row records what the student says about themselves, with no
     * proof behind it, so it is the weakest source here - deliberately below the FLM
     * transcript base (0.72) and far below an AI-analysed repository (up to 0.90).
     * At 0.60 a bare self-declaration can only fast-track LOW-importance nodes; core
     * and foundational skills still have to be earned through real evidence.
     * Kept in step with SkillEvidenceServiceImpl.SELF_REPORT_CONFIDENCE, which stamps
     * the matching MANUAL evidence row.
     */
    private static final BigDecimal PROFILE_SKILL_CONFIDENCE = new BigDecimal("0.60");

    /**
     * Confidence for a profile skill, by how much of it the student claims.
     *
     * <p>The flat {@link #PROFILE_SKILL_CONFIDENCE} above treats "I have heard of
     * Docker" and "I run Docker in production" as the same claim, and 0.60 is
     * below the bar on every node that can accept evidence: all 29
     * EVIDENCE_ALLOWED nodes on the Backend roadmap carry
     * {@code required_proficiency = 65}. So the profile source could never
     * produce a candidate — not rarely, never, for any student, on any node.
     * Measured: zero rows in {@code roadmap_recommendation_items}.
     *
     * <p>The rungs are the behaviourally-anchored scale the assessment already
     * writes. PRACTICED still sits under 0.65 on purpose: having practised
     * something is not grounds to skip learning it. APPLIED is.
     */
    private static final BigDecimal[] PROFICIENCY_CONFIDENCE = {
            new BigDecimal("0.00"),   // unset
            new BigDecimal("0.40"),   // AWARE
            new BigDecimal("0.55"),   // PRACTICED
            new BigDecimal("0.70"),   // APPLIED
            new BigDecimal("0.85")    // PROFESSIONAL
    };

    /**
     * Added when the row carries {@code verified_by}.
     *
     * <p>Same rule {@code SkillEvidenceService} applies when a repository read
     * and a self-report disagree: the read wins. This is that rule reaching the
     * roadmap, and it is what makes connecting GitHub change what the student
     * sees rather than only what their level says.
     */
    private static final BigDecimal VERIFIED_BONUS = new BigDecimal("0.10");
    private static final BigDecimal MAX_PROFILE_CONFIDENCE = new BigDecimal("0.95");

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

    /**
     * Matches RoadmapSelectionServiceImpl.CHOOSE_ONE: a node whose children are mutually
     * exclusive alternatives (e.g. "Pick a language" -> Java/Python/Go/...).
     */
    private static final String CHOOSE_ONE_SELECTION = "CHOOSE_ONE";

    @Value("${roadmap.parent-completion-threshold:0.6}")
    private double parentCompletionThreshold;

    private final AuthenticatedStudentService authenticatedStudentService;
    private final RoadmapRecommendationRepository recommendationRepository;
    private final RoadmapRecommendationItemRepository recommendationItemRepository;
    private final StudentSkillEvidenceRepository evidenceRepository;
    private final StudentProgressRepository studentProgressRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final StudentNodeSelectionRepository selectionRepository;
    private final SkillNodeRepository skillNodeRepository;
    private final CareerRequiredSkillRepository careerRequiredSkillRepository;
    private final RoadmapRecommendationMapper recommendationMapper;
    private final RoadmapProgressCalculator progressCalculator;
    private final RoadmapSelectionResolver roadmapSelectionResolver;
    private final SkillProficiencyPromoter skillProficiencyPromoter;
    private final AbilityFloorPropagator abilityFloorPropagator;
    private final StudentLevelService studentLevelService;

    @Transactional
    @Override
    public List<RoadmapRecommendationResponse> generateRecommendationsForCurrentStudent() {
        log.info("RoadmapPersonalizationServiceImpl: Generate recommendations request received");

        Student student = authenticatedStudentService.getRequiredStudent();
        UUID careerId = requireSelectedCareer(student);

        List<SkillNode> careerNodes = skillNodeRepository.findPublishedForCareer(careerId);
        if (careerNodes.isEmpty()) {
            log.info("RoadmapPersonalizationServiceImpl: Career {} has no roadmap nodes, nothing to recommend", careerId);
            return List.of();
        }

        Set<UUID> excludedNodeIds = new HashSet<>();
        excludedNodeIds.addAll(findCompletedNodeIds(student.getUserId()));
        excludedNodeIds.addAll(findNodeIdsInPendingRecommendations(student.getUserId()));
        // Never recommend completing an off-path alternative (e.g. C# once the student
        // chose Java); acceptRecommendation writes progress directly, bypassing gating.
        excludedNodeIds.addAll(roadmapSelectionResolver.resolve(student.getUserId(), careerNodes).progressExcluded());

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
        syncCompletedTopics(student, completedNodes);
        syncCompletedSkillsToProfile(student, completedNodes);
        linkAndAcceptEvidence(student, items, completedNodes);
        // Runs last, once the rows above exist: stamps proficiency and the verifying
        // source onto them. Without it every synced row stays proficiency-null, which
        // SeniorityCalculator ignores, so no amount of GitHub or transcript evidence
        // could ever move the student's level off the JUNIOR ceiling.
        skillProficiencyPromoter.promote(student.getUserId(), completedNodes, collectEvidenceIds(items));

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
                .completedNodeCount(completedNodes.size())
                .completedNodeIds(completedNodes.stream().map(SkillNode::getNodeId).toList())
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
        Map<String, List<SkillNode>> nodesByKeyword = new HashMap<>();
        Map<UUID, SkillNode> nodesById = new HashMap<>();
        for (SkillNode node : careerNodes) {
            nodesById.put(node.getNodeId(), node);
            for (String keyword : evidenceKeywords(node)) {
                nodesByKeyword.computeIfAbsent(keyword, key -> new ArrayList<>()).add(node);
            }
            if (node.getSkill() != null) {
                nodesBySkillId.computeIfAbsent(node.getSkill().getSkillId(), key -> new ArrayList<>()).add(node);
                if (node.getSkill().getSkillName() != null) {
                    nodesBySkillName
                            .computeIfAbsent(node.getSkill().getSkillName().toLowerCase(), key -> new ArrayList<>())
                            .add(node);
                }
            }
        }

        // Every node the two sources below matched by name, so the third pass can
        // ask what those nodes already cover. Recorded as they are offered rather
        // than re-derived, so a change to the matching rules cannot leave the
        // propagation reasoning about a different set of nodes than the one that
        // was actually proven.
        Map<UUID, BigDecimal> provenConfidence = new LinkedHashMap<>();
        Map<UUID, String> provenReason = new HashMap<>();
        Map<UUID, UUID> provenEvidenceId = new HashMap<>();

        // Source 1: skills the student already holds in their profile.
        for (StudentSkill studentSkill : studentSkillRepository.findByStudent_UserId(student.getUserId())) {
            if (studentSkill.getSkill() == null) {
                continue;
            }
            BigDecimal confidence = profileConfidence(studentSkill);
            String reason = profileReason(studentSkill);
            for (SkillNode node : nodesBySkillId.getOrDefault(studentSkill.getSkill().getSkillId(), List.of()).stream()
                    .filter(candidate -> directlyRepresentsSkill(candidate, studentSkill.getSkill().getSkillName()))
                    .toList()) {
                offerCandidate(candidates, excludedNodeIds, node, confidence, reason, null, importanceBySkillId,
                        studentSkill.getVerifiedBy() != null && !studentSkill.getVerifiedBy().isBlank());
                // A profile row propagates only when a source outside the student
                // signed it: verified_by is set by GitHub, a transcript or a mentor.
                recordProven(provenConfidence, provenReason, provenEvidenceId, node, confidence, reason, null,
                        studentSkill.getVerifiedBy() != null && !studentSkill.getVerifiedBy().isBlank());
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
                SkillNode node = nodesById.get(evidence.getNodeId());
                offerCandidate(candidates, excludedNodeIds,
                        node, confidence, reason, evidence.getEvidenceId(), importanceBySkillId,
                        isExternallyAttested(evidence));
                recordProven(provenConfidence, provenReason, provenEvidenceId,
                        node, confidence, reason, evidence.getEvidenceId(), canPropagateCapability(evidence));
            } else if (evidence.getSkillName() != null) {
                String skillName = evidence.getSkillName().toLowerCase();
                List<SkillNode> matched = nodesBySkillName.get(skillName);
                if (matched != null) {
                    matched = matched.stream()
                            .filter(candidate -> directlyRepresentsSkill(candidate, evidence.getSkillName()))
                            .toList();
                }
                if (matched == null) {
                    // No node carries this exact skill. Fall back to the node's own
                    // evidence_keywords, which is where a roadmap says "React counts as
                    // Frontend Framework". The column was seeded for every node and then
                    // never read, so evidence for a specific technology was discarded even
                    // when the node it belongs to named it outright.
                    matched = nodesByKeyword.getOrDefault(skillName, List.of());
                }
                if (matched.isEmpty()) {
                    // Still nothing: AI/GitHub-extracted skill names rarely match the
                    // roadmap's canonical wording exactly ("React.js" vs "React", "Node"
                    // vs "Node.js"). Without this, that evidence can never surface in any
                    // recommendation and is stuck PENDING forever. Fall back to a
                    // whole-word substring match in either direction.
                    matched = fuzzyMatchNodes(skillName, nodesBySkillName, nodesByKeyword);
                }
                for (SkillNode node : matched) {
                    offerCandidate(candidates, excludedNodeIds, node, confidence, reason, evidence.getEvidenceId(), importanceBySkillId, isExternallyAttested(evidence));
                    recordProven(provenConfidence, provenReason, provenEvidenceId,
                            node, confidence, reason, evidence.getEvidenceId(), canPropagateCapability(evidence));
                }
            }
        }


        // Source 3: the sub-skills a proven skill already covers.
        //
        // Sources 1 and 2 reach a node only by naming it, and a roadmap does not
        // name a skill twice: an imported repository proves `Java`, and Java's own
        // 71 sub-nodes carry their own catalog skills the student has evidence for
        // none of. So the system graded someone a Java developer in the header and
        // then asked them to tick `Exception Handling` by hand.
        //
        // Runs last, and only offers — every gate in offerCandidate still decides.
        // See AbilityFloorPropagator for why the tier bound is strict and why the
        // confidence is discounted rather than inherited whole.
        String level = studentLevelService.levelOf(student.getUserId())
                .map(current -> current.getLevel())
                .orElse(null);
        for (SkillNode covered : abilityFloorPropagator.coveredDescendants(
                provenConfidence.keySet(), careerNodes, level)) {
            UUID sourceNodeId = nearestProvenAncestor(covered, provenConfidence.keySet());
            if (sourceNodeId == null) {
                continue;
            }
            BigDecimal inherited = abilityFloorPropagator.inheritedConfidence(provenConfidence.get(sourceNodeId));
            offerCandidate(candidates, excludedNodeIds, covered, inherited,
                    inheritedReason(provenReason.get(sourceNodeId)),
                    provenEvidenceId.get(sourceNodeId), importanceBySkillId,
                    // Everything in provenConfidence already cleared the same gate
                    // in recordProven, so this is true by construction. Passed
                    // explicitly rather than hardcoded so the two cannot drift.
                    true);
        }

        return candidates;
    }

    /**
     * Remembers a directly-matched node so source 3 can ask what it covers.
     *
     * <p><b>Only externally-attested proof propagates.</b> Marking one node from a
     * self-report is a claim about that node; using it to mark everything beneath
     * it is that claim being cited as proof of a hundred more. Measured on the live
     * account: the graded paper wrote {@code source_type = MANUAL} at 0.80 for
     * {@code Java}, source 3 inherited 0.68, and 24 nodes ticked themselves —
     * {@code Enums}, {@code Record}, {@code Method Chaining}, {@code Initializer
     * Block}. Answering a handful of Java questions on a mixed Backend paper is
     * not evidence of any of those.
     *
     * <p>This is the same line {@code 2026-08-06_declared_skill_cleanup_2.sql}
     * draws, and for the same reason: a student's own statement cannot corroborate
     * itself. It is read off the evidence row rather than from a list of node ids,
     * so the gate follows the data instead of a table someone has to maintain.
     *
     * @param externallyAttested false for MANUAL evidence and bare self-declaration
     */
    private void recordProven(Map<UUID, BigDecimal> confidenceByNode, Map<UUID, String> reasonByNode,
                              Map<UUID, UUID> evidenceByNode, SkillNode node, BigDecimal confidence,
                              String reason, UUID evidenceId, boolean externallyAttested) {
        if (node == null || node.getNodeId() == null || confidence == null || !externallyAttested) {
            return;
        }
        // Strongest wins, matching offerCandidate: two repositories proving Java
        // should propagate the better of the two, not whichever was read last.
        BigDecimal existing = confidenceByNode.get(node.getNodeId());
        if (existing != null && existing.compareTo(confidence) >= 0) {
            return;
        }
        confidenceByNode.put(node.getNodeId(), confidence);
        reasonByNode.put(node.getNodeId(), reason);
        if (evidenceId != null) {
            evidenceByNode.put(node.getNodeId(), evidenceId);
        }
    }

    /**
     * The closest proven node above this one.
     *
     * <p>Nested proofs are possible — Java is proven, and so is Collections inside
     * it — and the nearer one is the better claim about what sits under it.
     */
    private UUID nearestProvenAncestor(SkillNode node, Set<UUID> provenNodeIds) {
        SkillNode current = node == null ? null : node.getParentNode();
        int guard = 0;
        while (current != null && current.getNodeId() != null && guard++ < MAX_ANCESTOR_WALK) {
            if (provenNodeIds.contains(current.getNodeId())) {
                return current.getNodeId();
            }
            current = current.getParentNode();
        }
        return null;
    }

    /**
     * The reason, rewritten so it cannot be read as direct proof.
     *
     * <p>The recommendation row is what a student sees when they ask why a node
     * ticked itself, and "verified by github" on a node no repository mentioned
     * would be false. This says what actually happened.
     */
    private String inheritedReason(String directReason) {
        String source = directReason == null || directReason.isBlank() ? "a skill you have proven" : directReason;
        return "covered by " + source + ", and below your current level";
    }

    /** Depth bound on the parent walk, so a cyclic parent link cannot hang a request. */
    private static final int MAX_ANCESTOR_WALK = 32;

    /**
     * True when something other than the student said this.
     *
     * <p>{@code MANUAL} covers both the skill picker's self-declaration and the
     * assessment writing its own result back, so it is the one source that cannot
     * corroborate a claim about anything except the node it names. GITHUB_PROJECT,
     * TRANSCRIPT and CHAT_FILE all rest on an artefact somebody can go and read.
     */
    private boolean isExternallyAttested(StudentSkillEvidence evidence) {
        return evidence != null && evidence.getSourceType() != null
                && evidence.getSourceType() != EvidenceType.MANUAL;
    }

    @Transactional
    @Override
    public void reconcileCompletedTopicsForCurrentStudent() {
        Student student = authenticatedStudentService.getRequiredStudent();
        List<SkillNode> completedNodes = studentProgressRepository.findByStudent_UserId(student.getUserId()).stream()
                .filter(progress -> progress.getStatus() == RoadmapStepStatus.COMPLETED)
                .map(StudentProgress::getSkillNode)
                .filter(java.util.Objects::nonNull)
                .toList();
        syncCompletedTopics(student, completedNodes);
    }

    /**
     * Evidence may establish a learning floor without becoming public verification.
     * An AI-graded assessment is still MANUAL for portfolio/level verification,
     * but its capability score is strong enough to skip basic descendants after
     * the normal confidence, completion-policy and career-importance gates run.
     */
    private boolean canPropagateCapability(StudentSkillEvidence evidence) {
        return isExternallyAttested(evidence)
                || (evidence != null && "self-assessment".equalsIgnoreCase(evidence.getDetectedBy()));
    }

    /**
     * Formatting-alias fallback for names such as {@code React.js}/{@code React} and
     * {@code Node.js}/{@code Node}.
     *
     * <p>This deliberately is not a substring matcher. A verified broad skill such as
     * {@code API} is not direct proof of {@code API Performance}, {@code API Security},
     * or every other roadmap node containing that word. The old bidirectional substring
     * rule did exactly that and could complete advanced nodes while their foundations
     * remained untouched.
     */
    static List<SkillNode> fuzzyMatchNodes(String skillName,
                                           Map<String, List<SkillNode>> nodesBySkillName,
                                           Map<String, List<SkillNode>> nodesByKeyword) {
        String normalizedEvidence = formattingAlias(skillName);
        if (normalizedEvidence.isBlank()) {
            return List.of();
        }
        Map<UUID, SkillNode> matched = new LinkedHashMap<>();
        for (Map<String, List<SkillNode>> source : List.of(nodesBySkillName, nodesByKeyword)) {
            for (Map.Entry<String, List<SkillNode>> entry : source.entrySet()) {
                if (normalizedEvidence.equals(formattingAlias(entry.getKey()))) {
                    for (SkillNode node : entry.getValue()) {
                        matched.put(node.getNodeId(), node);
                    }
                }
            }
        }
        return List.copyOf(matched.values());
    }

    private static String formattingAlias(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[._-]+", " ")
                .replaceAll("\\s+", " ");
        // In ecosystem names, the optional JS suffix is formatting rather than a
        // broader/narrower capability claim: React.js and React name the same tool.
        return normalized.endsWith(" js")
                ? normalized.substring(0, normalized.length() - 3).trim()
                : normalized;
    }

    /** Adds or merges a candidate, keeping the highest confidence and every supporting evidence id. */
    /**
     * How much a profile row is worth as grounds to skip a node.
     *
     * <p>Falls back to the old flat value when proficiency was never recorded, so
     * a row written before the assessment existed behaves exactly as it did.
     */
    private BigDecimal profileConfidence(StudentSkill studentSkill) {
        Short proficiency = studentSkill.getProficiency();
        if (proficiency == null || proficiency < 1 || proficiency >= PROFICIENCY_CONFIDENCE.length) {
            return PROFILE_SKILL_CONFIDENCE;
        }
        BigDecimal confidence = PROFICIENCY_CONFIDENCE[proficiency];
        if (studentSkill.getVerifiedBy() != null && !studentSkill.getVerifiedBy().isBlank()) {
            confidence = confidence.add(VERIFIED_BONUS).min(MAX_PROFILE_CONFIDENCE);
        }
        return confidence;
    }

    /** Names the grounds, including how strong they are — "you have it" is not a reason. */
    private String profileReason(StudentSkill studentSkill) {
        StringBuilder reason = new StringBuilder("You already have '")
                .append(studentSkill.getSkill().getSkillName())
                .append('\'');
        Short proficiency = studentSkill.getProficiency();
        if (proficiency != null && proficiency >= 1 && proficiency <= 4) {
            reason.append(" at ").append(switch (proficiency.intValue()) {
                case 1 -> "AWARE";
                case 2 -> "PRACTICED";
                case 3 -> "APPLIED";
                default -> "PROFESSIONAL";
            });
        }
        if (studentSkill.getVerifiedBy() != null && !studentSkill.getVerifiedBy().isBlank()) {
            reason.append(", verified by ").append(studentSkill.getVerifiedBy().toLowerCase(Locale.ROOT));
        } else {
            reason.append(", self-declared");
        }
        return reason.toString();
    }

    private void offerCandidate(Map<UUID, NodeCandidate> candidates, Set<UUID> excludedNodeIds,
                                SkillNode node, BigDecimal confidence, String reason, UUID evidenceId,
                                Map<UUID, ImportanceLevel> importanceBySkillId, boolean externallyAttested) {
        // Only proof from outside the student may complete a node on their behalf.
        //
        // Measured on the live account: the graded paper wrote a MANUAL evidence
        // row for `OOP` at 0.80, `OOP` is an evidence keyword on 17 Java nodes, and
        // all 17 ticked themselves — `Attributes and Methods`, `Enums`, `Record`,
        // `Method Chaining`. One answer on a mixed Backend paper is not proof of
        // any of them, and the student is right that nothing about picking Java
        // demonstrates knowing all of Java.
        //
        // A self-report still does everything it always did: it sets the student's
        // level, feeds the Skill Map, and shapes what the assessment asks next. It
        // just no longer completes work on their behalf. GITHUB_PROJECT and
        // TRANSCRIPT rest on an artefact somebody can go and read, so they still do.
        if (!externallyAttested) {
            return;
        }
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

    /**
     * The node's declared evidence synonyms, lowercased.
     *
     * <p>Matched whole against an evidence skill name rather than as substrings: the list
     * holds generic words like "types" and "package" that would otherwise pull in anything.
     */
    private static List<String> evidenceKeywords(SkillNode node) {
        JsonNode keywords = node.getEvidenceKeywords();
        if (keywords == null || !keywords.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode keyword : keywords) {
            String value = keyword.asText("").trim().toLowerCase();
            if (!value.isEmpty()) {
                result.add(value);
            }
        }
        return result;
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
            syncChosenAlternative(student, node);
            completedNodes.add(node);
        }
        return completedNodes;
    }

    /**
     * Recommendation acceptance writes progress directly, so it must perform the
     * same parent-topic reconciliation as a manual node completion. Without this,
     * evidence could complete Java leaves while Java/Pick a Language on the main
     * roadmap remained visually unfinished forever.
     */
    private void syncCompletedTopics(Student student, List<SkillNode> completedNodes) {
        if (completedNodes == null || completedNodes.isEmpty()
                || student.getCareerRole() == null || student.getCareerRole().getCareerId() == null) {
            return;
        }
        List<SkillNode> careerNodes = skillNodeRepository
                .findByCareerRole_CareerId(student.getCareerRole().getCareerId());
        SelectionView selectionView = roadmapSelectionResolver.resolve(student.getUserId(), careerNodes);
        Map<UUID, SkillNode> topics = new HashMap<>();
        for (SkillNode completedNode : completedNodes) {
            SkillNode topic = completedNode.getParentNode();
            while (topic != null) {
                topics.put(topic.getNodeId(), topic);
                topic = topic.getParentNode();
            }
        }
        // Children first. Processing an ancestor while sibling topics are still
        // unreconciled freezes a 46/67 branch in grey even though it crossed 60%.
        topics.values().stream()
                .sorted(java.util.Comparator.comparingInt(
                        RoadmapPersonalizationServiceImpl::nodeDepth).reversed())
                .forEach(topic -> syncOneTopic(student, topic, selectionView));
    }

    private static int nodeDepth(SkillNode node) {
        int depth = 0;
        for (SkillNode current = node; current != null; current = current.getParentNode()) depth++;
        return depth;
    }

    /** A shared category skill must not prove every leaf that happens to reuse its id. */
    private static boolean directlyRepresentsSkill(SkillNode node, String skillName) {
        if (node == null || skillName == null || skillName.isBlank()) return false;
        String normalized = skillName.trim().toLowerCase(Locale.ROOT);
        if (node.getNodeName() != null
                && node.getNodeName().trim().toLowerCase(Locale.ROOT).equals(normalized)) {
            return true;
        }
        return evidenceKeywords(node).contains(normalized);
    }

    private void syncOneTopic(Student student, SkillNode topic, SelectionView selectionView) {
        long totalWeight = 0;
        long doneWeight = 0;
        for (SkillNode child : skillNodeRepository.findByParentNode_NodeId(topic.getNodeId())) {
            if (selectionView.isExcludedFromProgress(child.getNodeId())) continue;
            int weight = child.getType() != null && child.getType().getWeight() != null
                    && child.getType().getWeight() > 0 ? child.getType().getWeight() : 1;
            totalWeight += weight;
            StudentProgress progress = studentProgressRepository
                    .findByStudent_UserIdAndSkillNode_NodeId(student.getUserId(), child.getNodeId())
                    .orElse(null);
            if (progress != null && progress.getStatus() == RoadmapStepStatus.COMPLETED) {
                doneWeight += weight;
            }
        }
        if (totalWeight == 0 || (double) doneWeight / totalWeight < parentCompletionThreshold) return;

        StudentProgress topicProgress = studentProgressRepository
                .findByStudent_UserIdAndSkillNode_NodeId(student.getUserId(), topic.getNodeId())
                .orElseGet(() -> StudentProgress.builder()
                        .student(Student.builder().userId(student.getUserId()).build())
                        .skillNode(topic)
                        .createdAt(LocalDateTime.now())
                        .build());
        if (topicProgress.getStatus() != RoadmapStepStatus.COMPLETED) {
            topicProgress.setStatus(RoadmapStepStatus.COMPLETED);
            topicProgress.setCompletedAt(LocalDateTime.now());
            studentProgressRepository.save(topicProgress);
            log.info("RoadmapPersonalizationServiceImpl: auto-completed parent topic '{}' ({}/{})",
                    topic.getNodeName(), doneWeight, totalWeight);
        }
    }

    /**
     * When the completed node is one alternative of a CHOOSE_ONE group (e.g. "Java" under
     * "Pick a language"), record it as the student's chosen alternative too. Without this,
     * accepting a recommendation only writes student_progress: the roadmap UI's "picked"
     * highlight reads exclusively from student_node_selections (RoadmapSelectionServiceImpl),
     * so the node showed as completed but never as the chosen option in its picker group.
     */
    private void syncChosenAlternative(Student student, SkillNode node) {
        SkillNode group = node.getParentNode();
        if (group == null || !CHOOSE_ONE_SELECTION.equalsIgnoreCase(group.getSelection())) {
            return;
        }
        StudentNodeSelection selection = selectionRepository
                .findByStudent_UserIdAndGroupNode_NodeId(student.getUserId(), group.getNodeId())
                .orElseGet(() -> StudentNodeSelection.builder()
                        .student(Student.builder().userId(student.getUserId()).build())
                        .groupNode(group)
                        .build());
        selection.setChosenNode(node);
        selectionRepository.save(selection);
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
    /** Every evidence id referenced by the accepted items, de-duplicated. */
    private Set<UUID> collectEvidenceIds(List<RoadmapRecommendationItem> items) {
        Set<UUID> evidenceIds = new HashSet<>();
        for (RoadmapRecommendationItem item : items) {
            if (item.getEvidenceIds() != null) {
                evidenceIds.addAll(item.getEvidenceIds());
            }
        }
        return evidenceIds;
    }

    private void linkAndAcceptEvidence(Student student, List<RoadmapRecommendationItem> items, List<SkillNode> completedNodes) {
        Set<UUID> evidenceIds = collectEvidenceIds(items);
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
                .findPublishedForCareer(student.getCareerRole().getCareerId());
        List<StudentProgress> progresses = studentProgressRepository.findByStudent_UserId(student.getUserId());
        // Count only the student's active path so the reported % matches the roadmap view.
        SelectionView selectionView = roadmapSelectionResolver.resolve(student.getUserId(), careerNodes);
        return progressCalculator.calculateProgress(selectionView.activePathNodes(careerNodes), progresses);
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
