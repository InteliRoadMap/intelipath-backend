package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.response.roadmap.FptSubjectRefResponse;

import com.inteliroadmap.backend.components.ResolvedOrder;
import com.inteliroadmap.backend.components.RoadmapEdgeResolver;
import com.inteliroadmap.backend.components.RoadmapProgressCalculator;
import com.inteliroadmap.backend.components.RoadmapSelectionResolver;
import com.inteliroadmap.backend.components.PersonalizedPlanBuilder;
import com.inteliroadmap.backend.components.RoadmapVisibilityFilter;
import com.inteliroadmap.backend.components.SeniorityCalculator;
import com.inteliroadmap.backend.domain.dto.response.roadmap.CoreSkillResponse;
import com.inteliroadmap.backend.components.RoadmapTierResolver;
import com.inteliroadmap.backend.components.SubRoadmapClassifier;
import com.inteliroadmap.backend.domain.dto.response.plan.LearningPlanResponse;
import com.inteliroadmap.backend.components.SelectionView;
import com.inteliroadmap.backend.components.StudentRoadmapContext;
import com.inteliroadmap.backend.domain.dto.response.student.StudentLevelResponse;
import com.inteliroadmap.backend.domain.enums.ImportanceLevel;
import com.inteliroadmap.backend.domain.enums.SeniorityLevel;
import com.inteliroadmap.backend.mappers.RoadmapEdgeMapper;
import com.inteliroadmap.backend.services.StudentLevelService;
import com.inteliroadmap.backend.domain.dto.request.UpdateNodeProgressRequest;
import com.inteliroadmap.backend.domain.dto.response.roadmap.FptNodeCoverageResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.FptNodeResourceResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapNodeResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapNodeEvidenceResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapTopicResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapSkillResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.StudentRoadmapResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapCrumbResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapRootResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.SubRoadmapResponse;
import com.inteliroadmap.backend.domain.entity.CareerRequiredSkill;
import com.inteliroadmap.backend.domain.entity.CareerRole;
import com.inteliroadmap.backend.domain.entity.FptSubject;
import com.inteliroadmap.backend.domain.entity.FptSubjectResource;
import com.inteliroadmap.backend.domain.entity.FptSubjectSkill;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.entity.RoadmapNodeLayout;
import com.inteliroadmap.backend.domain.entity.SkillNode;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.StudentProgress;
import com.inteliroadmap.backend.domain.entity.StudentSkill;
import com.inteliroadmap.backend.domain.entity.StudentSkillEvidence;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.domain.enums.AccountType;
import com.inteliroadmap.backend.domain.enums.RoadmapStepStatus;
import com.inteliroadmap.backend.repositories.CareerRequiredSkillRepository;
import com.inteliroadmap.backend.repositories.CareerRoleRepository;
import com.inteliroadmap.backend.repositories.FptSubjectRepository;
import com.inteliroadmap.backend.repositories.FptSubjectResourceRepository;
import com.inteliroadmap.backend.repositories.FptSubjectSkillRepository;
import com.inteliroadmap.backend.repositories.RoadmapNodeLayoutRepository;
import com.inteliroadmap.backend.repositories.SkillNodeRepository;
import com.inteliroadmap.backend.repositories.SkillRepository;
import com.inteliroadmap.backend.repositories.StudentProgressRepository;
import com.inteliroadmap.backend.repositories.StudentRepository;
import com.inteliroadmap.backend.repositories.StudentSkillRepository;
import com.inteliroadmap.backend.repositories.StudentSkillEvidenceRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.exceptions.ForbiddenException;
import com.inteliroadmap.backend.security.SecurityUtils;
import com.inteliroadmap.backend.domain.dto.response.market.SkillDemandResponse;
import com.inteliroadmap.backend.services.AuthenticatedStudentService;
import com.inteliroadmap.backend.services.MarketDemandService;
import com.inteliroadmap.backend.services.RoadmapSelectionService;
import com.inteliroadmap.backend.services.RoadmapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of the {@link RoadmapService} interface.
 * Manages the generation, retrieval, and progress tracking of career roadmaps for students.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoadmapServiceImpl implements RoadmapService {

    private final RoadmapProgressCalculator roadmapProgressCalculator;
    private final RoadmapNodeLayoutRepository roadmapNodeLayoutRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final CareerRoleRepository careerRoleRepository;
    private final SkillNodeRepository skillNodeRepository;
    private final StudentProgressRepository studentProgressRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final StudentSkillEvidenceRepository studentSkillEvidenceRepository;
    private final CareerRequiredSkillRepository careerRequiredSkillRepository;
    private final SkillRepository skillRepository;
    private final FptSubjectRepository fptSubjectRepository;
    private final FptSubjectSkillRepository fptSubjectSkillRepository;
    private final FptSubjectResourceRepository fptSubjectResourceRepository;
    private final RoadmapSelectionService roadmapSelectionService;
    private final MarketDemandService marketDemandService;
    private final RoadmapSelectionResolver roadmapSelectionResolver;
    private final RoadmapEdgeResolver roadmapEdgeResolver;
    private final SeniorityCalculator seniorityCalculator;
    private final RoadmapVisibilityFilter roadmapVisibilityFilter;
    private final SubRoadmapClassifier subRoadmapClassifier;
    private final RoadmapTierResolver roadmapTierResolver;
    private final PersonalizedPlanBuilder personalizedPlanBuilder;
    private final RoadmapEdgeMapper roadmapEdgeMapper;
    private final StudentLevelService studentLevelService;

    private static final String COMPLETED_STATUS = "COMPLETED";
    private static final String FRONTEND_COMPLETED_STATUS = "completed";
    private static final String FRONTEND_IN_PROGRESS_STATUS = "in_progress";
    private static final String FRONTEND_CURRENT_STATUS = "current";
    private static final String FRONTEND_LOCKED_STATUS = "locked";
    // An alternative in a CHOOSE_ONE group the student did not pick: shown greyed
    // out, not part of the active path, and not counted toward progress.
    private static final String FRONTEND_ALTERNATIVE_STATUS = "alternative";
    private final AuthenticatedStudentService authenticatedStudentService;

    /**
     * Fraction of a topic's child weight that must be COMPLETED before the topic
     * (spine) node itself auto-completes. A topic like "Internet" finishes once
     * its sub-skills (HTTP, DNS, Hosting...) cross this threshold — the student
     * never marks the topic manually. Configurable via {@code roadmap.parent-completion-threshold}.
     */
    @Value("${roadmap.parent-completion-threshold:0.6}")
    private double parentCompletionThreshold;

    /**
     * Securely identifies the currently authenticated student.
     * Extracts the user email from the SecurityContextHolder and retrieves the corresponding Student profile.
     *
     * @return The authenticated Student entity
     * @throws ResourceNotFoundException if the token is invalid or the student profile is missing
     */
    private Student getAuthenticatedStudent() {
        return authenticatedStudentService.getOrCreateStudent();
    }

    /**
     * Builds the authenticated student's target roadmap for the frontend contract.
     * Business rules:
     * - Student is resolved from JWT, never from request parameters.
     * - Node IDs are real UUID values from skill_nodes.
     * - Status is translated to frontend lowercase values only.
     * - Missing roadmap/progress data returns empty arrays/default progress, not null.
     *
     * @return student roadmap response
     */
    @Transactional(readOnly = true)
    @Override
    public StudentRoadmapResponse getStudentRoadmap() {
        return getStudentRoadmap(Set.of());
    }

    @Transactional(readOnly = true)
    @Override
    public StudentRoadmapResponse getStudentRoadmap(Set<UUID> expandedNodeIds) {
        log.info("RoadmapServiceImpl: Fetching authenticated student roadmap");

        Student student = getAuthenticatedStudent();
        // Seed any obvious CHOOSE_ONE picks from the student's skill profile before
        // reading the roadmap, so a Java student sees Java pre-selected on first load.
        // Runs in its own transaction; a failure here must not break the read.
        try {
            roadmapSelectionService.autoDefaultSelections();
        } catch (RuntimeException e) {
            log.warn("RoadmapServiceImpl: auto-default selections skipped: {}", e.getMessage());
        }

        return buildStudentRoadmap(student, expandedNodeIds);
    }

    @Transactional(readOnly = true)
    @Override
    public StudentRoadmapResponse getStudentRoadmapForPortfolio(Student student) {
        return buildStudentRoadmap(student, Set.of());
    }

    /** Builds without authentication side effects so public portfolio reads stay read-only. */
    private StudentRoadmapResponse buildStudentRoadmap(Student student, Set<UUID> expandedNodeIds) {
        if (student.getCareerRole() == null || student.getCareerRole().getCareerId() == null) {
            return StudentRoadmapResponse.builder()
                    .progress(0)
                    .nodes(List.of())
                    .build();
        }

        // Look up the career role and associated skill nodes
        CareerRole careerRole = careerRoleRepository.findByCareerId(student.getCareerRole().getCareerId());
        List<SkillNode> nodes = skillNodeRepository
                .findPublishedForCareerLegacyOrder(careerRole.getCareerId());

        if (nodes.isEmpty()) {
            return StudentRoadmapResponse.builder()
                    .targetCareerRole(careerRole.getCareerName())
                    .progress(0)
                    .nodes(List.of())
                    .build();
        }

        // Fetch student's progress for these specific nodes and map by node ID
        Map<UUID, StudentProgress> progressByNodeId = mapProgressByNodeId(
                studentProgressRepository.findByStudent_UserIdAndSkillNode_NodeIdIn(
                        student.getUserId(),
                        nodes.stream().map(SkillNode::getNodeId).toList()
                )
        );

        // Resolve the student's CHOOSE_ONE picks: which alternatives are off-path.
        SelectionView selectionView = roadmapSelectionResolver.resolve(student.getUserId(), nodes);

        // Market demand for this career's skills, fetched once and reused by both
        // the ordering below and the per-node decoration in the tree. Career-scoped:
        // the same skill ranks differently depending on the role it is read against.
        Map<UUID, SkillDemandResponse> demandBySkill = marketDemandSafe(careerRole.getCareerId());

        // Order the nodes for THIS student. Everything downstream — the unlock
        // gates, the per-node previousNode on the wire, the edge list — reads this
        // one result, so what the student sees and what unlocks cannot diverge.
        StudentRoadmapContext context = studentRoadmapContext(student, careerRole, demandBySkill);
        ResolvedOrder order = roadmapEdgeResolver.resolve(nodes, context, selectionView);

        // Compute frontend statuses (e.g., locked, current, completed) and overall progress.
        // Progress counts the active path only, so picking a language never dilutes the %.
        Map<UUID, String> statusByNodeId = buildFrontendStatusMap(nodes, progressByNodeId, selectionView, order);
        // Progress counts the WHOLE career, not the slice being drawn: a percentage
        // that moved because the student expanded a topic would be meaningless.
        int progress = calculateProgress(selectionView.activePathNodes(nodes), progressByNodeId);

        // Which of those nodes are worth sending. Statuses above were computed over
        // the full set first, so hiding a node can never change what a visible one
        // says about itself.
        Set<UUID> visibleIds = new HashSet<>(roadmapVisibilityFilter.visibleNodeIds(
                nodes, statusByNodeId, context.level(), expandedNodeIds,
                RoadmapVisibilityFilter.DEFAULT_MAX_DEPTH));
        // Selecting, not hiding. Once the student has picked Java, Laravel's 49
        // descendants are not their roadmap greyed out — they are not their
        // roadmap. The alternatives' own nodes stay, so the choice remains visible
        // and reversible; only what hangs below them goes.
        visibleIds.removeAll(selectionView.offPathDescendants());
        // A node carrying a curriculum inside it (Java 71, Python 122, ASP.NET
        // Core 157) is a place to go, not a step to walk past. Its contents come
        // off the path; the node itself always stays, which is what keeps this
        // safe to apply to every career including the ones that are nothing but
        // imported roadmaps.
        Map<UUID, Integer> subtreeSizes = subRoadmapClassifier.subtreeSizes(nodes);
        Set<UUID> enterableIds = subRoadmapClassifier.enterableNodes(nodes);
        visibleIds.removeAll(subRoadmapClassifier.nodesInsideEnterables(nodes, null));
        Map<UUID, Integer> hiddenChildren = roadmapVisibilityFilter.hiddenChildCounts(nodes, visibleIds);
        List<SkillNode> visibleNodes = nodes.stream()
                .filter(n -> visibleIds.contains(n.getNodeId()))
                .toList();

        // FPT material is only offered to FPT accounts; everyone else gets the public
        // roadmap.sh links that every node already carries.
        User user = userRepository.findByUserId(student.getUserId());
        boolean fptAccount = user != null && user.getAccountType() == AccountType.FPT;

        // Build the hierarchical roadmap tree
        return withReadiness(StudentRoadmapResponse.builder()
                .targetCareerRole(careerRole.getCareerName())
                .progress(progress)
                .nodes(buildRoadmapTree(visibleNodes, statusByNodeId, progressByNodeId, selectionView,
                        fptAccount, order, demandBySkill, hiddenChildren, nodes,
                        heldSkillsBySkillId(student.getUserId()), subtreeSizes, enterableIds,
                        context, student.getUserId()))
                // Only edges whose BOTH ends survived the filter: an edge pointing at
                // a node that was not sent would draw into empty space.
                .edges(roadmapEdgeMapper.toResponses(order.edges().stream()
                        .filter(e -> visibleIds.contains(e.source()) && visibleIds.contains(e.target()))
                        .toList()))
                .subRoadmaps(subRoadmapCards(nodes, statusByNodeId, null))
                .coreSkills(coreSkillsSafe(student, careerRole, demandBySkill)),
                readinessSafe(student, careerRole))
                .build();
    }

    /**
     * One sub-roadmap, opened.
     *
     * <p>Everything below {@code rootNodeId} and nothing else — but sliced the
     * same way the career roadmap is, rather than served whole.
     *
     * <p><b>This reverses an earlier decision, deliberately.</b> The rule used to
     * be "the student asked for this specific track by clicking into it, so
     * answering with a slice would be the same mistake as the '+13' badge that
     * delivered one node." That reasoning holds for the <em>level</em> filter and
     * is kept: nothing here is withheld because of a student's tier. It does not
     * hold for depth. Measured on the live data, entering {@code C#} drew 269
     * nodes at once, {@code Scala} 156 and {@code Golang} 124, five levels deep,
     * with no way to fold any of it away — because {@code hiddenChildren} was
     * passed as {@code null}, the "+N" affordance that makes the career view
     * navigable was not merely unused here, it could not appear.
     *
     * <p>So the depth cap and {@code ?expand=} come back, which is the same
     * contract as {@link #getStudentRoadmap(Set)} and the opposite of the old
     * failure: a badge that delivers a whole topic rather than one node.
     *
     * <p><b>Tier is still shown, never removed.</b> {@code tierLocked} continues
     * to travel on every node — see {@link RoadmapTierResolver} — so a beginner
     * opening C# still sees that its 134 advanced nodes exist and are waiting.
     * A locked node is a promise; a missing node is a shorter roadmap.
     */
    @Override
    @Transactional(readOnly = true)
    public StudentRoadmapResponse getStudentSubRoadmap(UUID rootNodeId, Set<UUID> expandedNodeIds) {
        log.info("RoadmapServiceImpl: Fetching sub-roadmap {}", rootNodeId);
        Student student = getAuthenticatedStudent();

        SkillNode root = skillNodeRepository.findById(rootNodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Roadmap node not found: " + rootNodeId));

        CareerRole careerRole = root.getCareerRole();
        List<SkillNode> careerNodes = skillNodeRepository
                .findPublishedForCareerLegacyOrder(careerRole.getCareerId());

        Map<UUID, List<SkillNode>> childrenByParent = new HashMap<>();
        for (SkillNode node : careerNodes) {
            if (node.getParentNode() != null) {
                childrenByParent
                        .computeIfAbsent(node.getParentNode().getNodeId(), key -> new ArrayList<>())
                        .add(node);
            }
        }

        Set<UUID> subtreeIds = new HashSet<>();
        Deque<SkillNode> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            SkillNode current = pending.pop();
            if (!subtreeIds.add(current.getNodeId())) {
                continue;
            }
            for (SkillNode child : childrenByParent.getOrDefault(current.getNodeId(), List.of())) {
                pending.push(child);
            }
        }
        // The root is named by the breadcrumb, so drawing it again would waste the
        // one slot at depth 0 — and leave it as the only node there, which is what
        // collapsed the sub-roadmap into a single column. Dropping it promotes its
        // children to the top level, where the layout can lay them out as a path.
        List<SkillNode> subtree = careerNodes.stream()
                .filter(node -> subtreeIds.contains(node.getNodeId()))
                .filter(node -> !node.getNodeId().equals(rootNodeId))
                .map(node -> relativeDepth(node, root))
                .toList();

        Map<UUID, StudentProgress> progressByNodeId = mapProgressByNodeId(
                studentProgressRepository.findByStudent_UserIdAndSkillNode_NodeIdIn(
                        student.getUserId(), subtree.stream().map(SkillNode::getNodeId).toList()));

        Map<UUID, SkillDemandResponse> demandBySkill = marketDemandSafe(careerRole.getCareerId());
        SelectionView selectionView = roadmapSelectionResolver.resolve(student.getUserId(), subtree);
        StudentRoadmapContext context = studentRoadmapContext(student, careerRole, demandBySkill);
        ResolvedOrder order = roadmapEdgeResolver.resolve(subtree, context, selectionView);
        Map<UUID, String> statusByNodeId =
                buildFrontendStatusMap(subtree, progressByNodeId, selectionView, order);

        // Statuses and progress above were computed over the WHOLE subtree, before
        // anything was hidden, exactly as the career view does it — so folding a
        // branch away can never change what a visible node says about itself, and
        // the percentage does not move when the student opens a topic.
        int progress = calculateProgress(selectionView.activePathNodes(subtree), progressByNodeId);

        // Depth only, stage rule off — see the overload's javadoc. The student
        // opened this track on purpose; capping it at a stage band would answer a
        // question they did not ask, and tierLocked already says "not yet" without
        // making the roadmap look shorter than it is.
        Set<UUID> visibleIds = new HashSet<>(roadmapVisibilityFilter.visibleNodeIds(
                subtree, statusByNodeId, context.level(), expandedNodeIds,
                RoadmapVisibilityFilter.DEFAULT_MAX_DEPTH, true));
        // A rejected alternative's descendants are not this student's roadmap; the
        // alternatives themselves stay, so the choice remains visible and
        // reversible. Same rule as the career view.
        visibleIds.removeAll(selectionView.offPathDescendants());
        Map<UUID, Integer> subtreeSizes = subRoadmapClassifier.subtreeSizes(subtree);
        Set<UUID> enterableIds = subRoadmapClassifier.enterableNodes(subtree);
        // A track nested inside this one (C# holds several) is a door, not a
        // branch to unfold in place.
        visibleIds.removeAll(subRoadmapClassifier.nodesInsideEnterables(subtree, null));
        Map<UUID, Integer> hiddenChildren = roadmapVisibilityFilter.hiddenChildCounts(subtree, visibleIds);
        List<SkillNode> visibleNodes = subtree.stream()
                .filter(node -> visibleIds.contains(node.getNodeId()))
                .toList();

        User user = userRepository.findByUserId(student.getUserId());
        boolean fptAccount = user != null && user.getAccountType() == AccountType.FPT;

        return withReadiness(StudentRoadmapResponse.builder()
                .targetCareerRole(root.getNodeName())
                .progress(progress)
                .nodes(buildRoadmapTree(visibleNodes, statusByNodeId, progressByNodeId, selectionView,
                        fptAccount, order, demandBySkill, hiddenChildren, subtree,
                        heldSkillsBySkillId(student.getUserId()),
                        subtreeSizes, enterableIds, context, student.getUserId()))
                // Only edges with both ends still on screen: an edge into a folded
                // branch would draw into empty space.
                .edges(roadmapEdgeMapper.toResponses(order.edges().stream()
                        .filter(e -> visibleIds.contains(e.source()) && visibleIds.contains(e.target()))
                        .toList()))
                .breadcrumb(breadcrumbTo(root, careerRole))
                .rootNode(rootNodeOf(root, subtree))
                .coreSkills(coreSkillsSafe(student, careerRole, demandBySkill)),
                readinessSafe(student, careerRole))
                .build();
    }

    /**
     * A copy of the node whose depth is measured from the view's root rather than
     * from the career's.
     *
     * <p>Depth is what tells every consumer — the filter, the layout, the client —
     * what the top level is. Inside a sub-roadmap the top level is the root's
     * children, so leaving the career-wide depth on them would put nothing at
     * level zero and everything one step below it.
     */
    private SkillNode relativeDepth(SkillNode node, SkillNode viewRoot) {
        if (node.getDepth() == null || viewRoot.getDepth() == null) {
            return node;
        }
        SkillNode copy = new SkillNode();
        org.springframework.beans.BeanUtils.copyProperties(node, copy);
        copy.setDepth((short) Math.max(0, node.getDepth() - viewRoot.getDepth() - 1));
        // A node whose parent is the view root has no parent inside this view.
        if (node.getParentNode() != null
                && node.getParentNode().getNodeId().equals(viewRoot.getNodeId())) {
            copy.setParentNode(null);
        }
        return copy;
    }

    /**
     * The view's root, described but not drawn.
     *
     * <p>{@code optionCount} is measured on the depth-relative subtree, where
     * depth 0 means "direct child of the root" — see {@link #relativeDepth}. So
     * for {@code Pick a Language} it is nine, and the client can tell a real
     * fork from a group with one option left after the publication gate.
     */
    private RoadmapRootResponse rootNodeOf(SkillNode root, List<SkillNode> subtree) {
        long options = subtree.stream()
                .filter(node -> node.getDepth() != null && node.getDepth() == 0)
                .count();
        return RoadmapRootResponse.builder()
                .nodeId(root.getNodeId())
                .name(root.getNodeName())
                .selection(root.getSelection())
                .chooseCount(root.getChooseCount())
                .nodeKind(root.getNodeKind())
                .optionCount((int) options)
                .build();
    }

    /** The trail from the career down to this node, outermost first. */
    private List<RoadmapCrumbResponse> breadcrumbTo(SkillNode node, CareerRole careerRole) {
        Deque<RoadmapCrumbResponse> trail = new ArrayDeque<>();
        SkillNode current = node;
        // Bounded rather than while(true): a parent cycle in the data would
        // otherwise hang the request, and a wrong breadcrumb beats a hung page.
        for (int guard = 0; current != null && guard < 32; guard++) {
            trail.push(RoadmapCrumbResponse.builder()
                    .nodeId(current.getNodeId())
                    .name(current.getNodeName())
                    .build());
            current = current.getParentNode();
        }
        trail.push(RoadmapCrumbResponse.builder()
                .name(careerRole == null ? null : careerRole.getCareerName())
                .build());
        return new ArrayList<>(trail);
    }

    /**
     * The cards offering each standalone roadmap under this career.
     *
     * <p>Counts come from the full node set, not the filtered one: a card that
     * said "12 nodes" because the depth filter had trimmed the rest would
     * misdescribe what the student is about to enter.
     */
    private List<SubRoadmapResponse> subRoadmapCards(List<SkillNode> nodes,
                                                     Map<UUID, String> statusByNodeId,
                                                     UUID chosenNodeId) {
        Map<UUID, List<SkillNode>> childrenByParent = new HashMap<>();
        for (SkillNode node : nodes) {
            if (node.getParentNode() != null) {
                childrenByParent
                        .computeIfAbsent(node.getParentNode().getNodeId(), key -> new ArrayList<>())
                        .add(node);
            }
        }

        List<SubRoadmapResponse> cards = new ArrayList<>();
        Set<UUID> enterable = subRoadmapClassifier.enterableNodes(nodes);
        for (SkillNode root : nodes.stream()
                .filter(n -> enterable.contains(n.getNodeId()))
                .filter(n -> n.getParentNode() == null || !enterable.contains(n.getParentNode().getNodeId()))
                .toList()) {
            Set<UUID> subtree = new HashSet<>();
            Deque<SkillNode> pending = new ArrayDeque<>();
            pending.push(root);
            while (!pending.isEmpty()) {
                SkillNode current = pending.pop();
                if (!subtree.add(current.getNodeId())) {
                    continue;
                }
                for (SkillNode child : childrenByParent.getOrDefault(current.getNodeId(), List.of())) {
                    pending.push(child);
                }
            }
            int completed = (int) subtree.stream()
                    .filter(id -> FRONTEND_COMPLETED_STATUS.equals(statusByNodeId.get(id)))
                    .count();
            cards.add(SubRoadmapResponse.builder()
                    .nodeId(root.getNodeId())
                    .name(root.getNodeName())
                    .description(root.getDescription())
                    .nodeCount(subtree.size())
                    .completedCount(completed)
                    .chosen(root.getNodeId().equals(chosenNodeId))
                    .build());
        }
        cards.sort(Comparator.comparing(SubRoadmapResponse::getChosen, Comparator.reverseOrder())
                .thenComparing(c -> c.getName() == null ? "" : c.getName()));
        return cards;
    }

    /**
     * Gathers the profile facts that can change the order of a roadmap.
     *
     * <p>Every lookup here is fail-soft. A student with no assessment, no skills
     * and no market data yields an empty context, and an empty context is exactly
     * what makes {@link RoadmapEdgeResolver} fall back to the static order — so
     * the worst case of this whole feature is today's roadmap, not a broken one.
     */
    private StudentRoadmapContext studentRoadmapContext(
            Student student,
            CareerRole careerRole,
            Map<UUID, SkillDemandResponse> demandBySkill
    ) {
        Map<UUID, Short> proficiencyBySkillId = new HashMap<>();
        Set<String> heldSkillNamesLower = new HashSet<>();
        for (StudentSkill studentSkill : studentSkillRepository.findByStudent_UserId(student.getUserId())) {
            if (studentSkill.getSkill() == null) {
                continue;
            }
            // A row with no proficiency still proves the student holds the skill —
            // that is what the roadmap sync writes — so it counts for ordering even
            // though it carries no level.
            proficiencyBySkillId.put(studentSkill.getSkill().getSkillId(),
                    studentSkill.getProficiency() != null ? studentSkill.getProficiency() : (short) 0);
            if (studentSkill.getSkill().getSkillName() != null) {
                heldSkillNamesLower.add(studentSkill.getSkill().getSkillName().trim().toLowerCase());
            }
        }

        Map<UUID, ImportanceLevel> importanceBySkillId = new HashMap<>();
        for (CareerRequiredSkill required : careerRequiredSkillRepository
                .findByCareerRole_CareerId(careerRole.getCareerId())) {
            if (required.getSkill() != null && required.getImportanceLevel() != null) {
                importanceBySkillId.put(required.getSkill().getSkillId(), required.getImportanceLevel());
            }
        }

        return new StudentRoadmapContext(proficiencyBySkillId, heldSkillNamesLower,
                studentLevelSafe(student.getUserId()), demandBySkill, importanceBySkillId);
    }

    /**
     * The student's level, or null when they skipped the assessment or it cannot
     * be computed. Null is a real answer here — it disables readiness scoring
     * rather than pretending the student is a FRESHER.
     */
    private SeniorityLevel studentLevelSafe(UUID userId) {
        try {
            return studentLevelService.levelOf(userId)
                    .map(StudentLevelResponse::getLevel)
                    .map(SeniorityLevel::fromString)
                    .orElse(null);
        } catch (Exception e) {
            log.warn("RoadmapServiceImpl: level unavailable for ordering, using the static "
                    + "roadmap order: {}", e.getMessage());
            return null;
        }
    }

    @Transactional(readOnly = true)
    @Override
    public LearningPlanResponse getStudentPlan() {
        Student student = getAuthenticatedStudent();
        if (student.getCareerRole() == null || student.getCareerRole().getCareerId() == null) {
            return LearningPlanResponse.builder()
                    .summary("Pick a target role first — until then there is nothing to measure a gap against.")
                    .steps(List.of())
                    .alreadyCovered(List.of())
                    .requiredSkillCount(0)
                    .coveredSkillCount(0)
                    .build();
        }
        CareerRole careerRole = careerRoleRepository.findByCareerId(student.getCareerRole().getCareerId());
        List<SkillNode> nodes = skillNodeRepository
                .findPublishedForCareerLegacyOrder(careerRole.getCareerId());

        // Statuses come from the roadmap's own gating, so the plan never offers a
        // node as "next" that the roadmap would refuse to let them start.
        Map<UUID, StudentProgress> progressByNodeId = mapProgressByNodeId(
                studentProgressRepository.findByStudent_UserIdAndSkillNode_NodeIdIn(
                        student.getUserId(), nodes.stream().map(SkillNode::getNodeId).toList()));
        SelectionView selectionView = roadmapSelectionResolver.resolve(student.getUserId(), nodes);
        Map<UUID, SkillDemandResponse> demandBySkill = marketDemandSafe(careerRole.getCareerId());
        StudentRoadmapContext context = studentRoadmapContext(student, careerRole, demandBySkill);
        ResolvedOrder order = roadmapEdgeResolver.resolve(nodes, context, selectionView);
        Map<UUID, String> statusByNodeId =
                buildFrontendStatusMap(nodes, progressByNodeId, selectionView, order);

        return personalizedPlanBuilder.build(
                careerRole.getCareerName(),
                context.level(),
                // Core only. The full table is the career-scoped catalog — 504 rows
                // for Frontend — and a plan measured against that reports "0 of
                // 504 covered", which tells the student nothing.
                careerRequiredSkillRepository.findByCareerRole_CareerIdAndImportanceLevelIn(
                        careerRole.getCareerId(), SeniorityCalculator.CORE_IMPORTANCE),
                studentSkillRepository.findByStudent_UserId(student.getUserId()),
                nodes,
                demandBySkill,
                statusByNodeId);
    }

    /**
     * Retrieves the roadmap template for a career without student progress.
     *
     * @param careerId career role UUID
     * @return roadmap template response
     */
    @Transactional(readOnly = true)
    @Override
    public StudentRoadmapResponse getCareerRoadmapTemplate(UUID careerId) {
        Optional<CareerRole> careerRoleOptional = careerRoleRepository.findById(careerId);
        if (careerRoleOptional.isEmpty()) {
            throw new ResourceNotFoundException("Career not found");
        }

        CareerRole careerRole = careerRoleOptional.get();
        List<SkillNode> nodes = skillNodeRepository
                .findPublishedForCareerLegacyOrder(careerId);
        List<RoadmapNodeResponse> nodeDtos = nodes.stream()
                .map(node -> mapToLegacyNodeDto(node, "not_started"))
                .toList();

        return StudentRoadmapResponse.builder()
                .targetCareerRole(careerRole.getCareerName())
                .progress(0)
                .nodes(nodeDtos)
                .build();
    }

    /**
     * Calculates the authenticated student's completion percentage for a career.
     *
     * @param careerId career role UUID
     * @return completion percentage from 0 to 100
     */
    @Transactional(readOnly = true)
    @Override
    public Integer getCareerProgress(UUID careerId) {
        Student student = getStudentFromContext();
        List<SkillNode> nodes = skillNodeRepository.findPublishedForCareer(careerId);
        if (nodes.isEmpty()) {
            return 0;
        }

        List<UUID> nodeIds = nodes.stream()
                .map(SkillNode::getNodeId)
                .toList();
        List<StudentProgress> progressList =
                studentProgressRepository.findByStudent_UserIdAndSkillNode_NodeIdIn(
                        student.getUserId(),
                        nodeIds
                );

        // Count only the student's active path (chosen alternatives), matching
        // /roadmaps/student. Delegate to the shared calculator (single source of truth).
        SelectionView selectionView = roadmapSelectionResolver.resolve(student.getUserId(), nodes);
        return roadmapProgressCalculator.calculateProgress(selectionView.activePathNodes(nodes), progressList);
    }

    /**
     * Retrieves one roadmap node using the existing roadmap API response.
     *
     * @param nodeId roadmap node UUID
     * @return roadmap node detail response
     */
    @Transactional(readOnly = true)
    @Override
    public RoadmapNodeResponse getNodeDetail(UUID nodeId) {
        Optional<SkillNode> nodeOptional = skillNodeRepository.findById(nodeId);
        if (nodeOptional.isEmpty()) {
            throw new ResourceNotFoundException("Node not found");
        }

        return mapToLegacyNodeDto(nodeOptional.get(), null);
    }

    /**
     * Creates or updates the authenticated student's progress for a roadmap node.
     *
     * @param request request body containing node ID and status
     */
    @Transactional
    @Override
    public void updateNodeProgress(UpdateNodeProgressRequest request) {
        Student student = getStudentFromContext();

        Optional<SkillNode> nodeOptional = skillNodeRepository.findById(request.getNodeId());
        if (nodeOptional.isEmpty()) {
            throw new ResourceNotFoundException("Node not found");
        }

        SkillNode node = nodeOptional.get();

        // Resolve the student's CHOOSE_ONE picks over this career once; used both to
        // block writes on off-path nodes and to gate topic auto-completion below.
        List<SkillNode> careerNodes = (node.getCareerRole() != null && node.getCareerRole().getCareerId() != null)
                ? skillNodeRepository.findByCareerRole_CareerIdOrderByNodeLevelAscNodeNameAsc(node.getCareerRole().getCareerId())
                : List.of();
        SelectionView selectionView = roadmapSelectionResolver.resolve(student.getUserId(), careerNodes);

        // The read path resolves order inside the currently open sub-roadmap. A
        // write checked against the whole career can otherwise reject a node the
        // same API just labelled CURRENT. Rebuild exactly that view, after proving
        // that the supplied root belongs to this career and contains the node.
        List<SkillNode> gateNodes = careerNodes;
        if (request.getContextRootNodeId() != null && !careerNodes.isEmpty()) {
            SkillNode contextRoot = careerNodes.stream()
                    .filter(candidate -> request.getContextRootNodeId().equals(candidate.getNodeId()))
                    .findFirst()
                    .orElseThrow(() -> new ForbiddenException("Invalid roadmap progress context."));
            Map<UUID, List<SkillNode>> contextChildren = new HashMap<>();
            for (SkillNode candidate : careerNodes) {
                if (candidate.getParentNode() != null) {
                    contextChildren.computeIfAbsent(candidate.getParentNode().getNodeId(), ignored -> new ArrayList<>())
                            .add(candidate);
                }
            }
            Set<UUID> contextIds = new HashSet<>();
            Deque<SkillNode> contextPending = new ArrayDeque<>();
            contextPending.push(contextRoot);
            while (!contextPending.isEmpty()) {
                SkillNode current = contextPending.pop();
                if (!contextIds.add(current.getNodeId())) continue;
                contextChildren.getOrDefault(current.getNodeId(), List.of()).forEach(contextPending::push);
            }
            if (!contextIds.contains(node.getNodeId())) {
                throw new ForbiddenException("Node does not belong to the supplied roadmap context.");
            }
            gateNodes = careerNodes.stream()
                    .filter(candidate -> contextIds.contains(candidate.getNodeId()))
                    .filter(candidate -> !candidate.getNodeId().equals(contextRoot.getNodeId()))
                    .map(candidate -> relativeDepth(candidate, contextRoot))
                    .toList();
        }

        // An alternative the student has not chosen (or any alternative while the
        // CHOOSE_ONE group is still undecided) is off the active path: the student
        // must pick it first before tracking progress on it.
        if (selectionView.isExcludedFromProgress(node.getNodeId())) {
            throw new ForbiddenException("Select this alternative for your roadmap before tracking progress on it.");
        }

        Optional<StudentProgress> progressOptional =
                studentProgressRepository.findByStudent_UserIdAndSkillNode_NodeId(student.getUserId(), node.getNodeId());

        StudentProgress progress;
        if (progressOptional.isPresent()) {
            progress = progressOptional.get();
        } else {
            progress = StudentProgress.builder()
                    .student(Student.builder().userId(student.getUserId()).build())
                    .skillNode(node)
                    .createdAt(LocalDateTime.now())
                    .build();
        }

        RoadmapStepStatus newStatus;
        try {
            newStatus = RoadmapStepStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            newStatus = RoadmapStepStatus.IN_PROGRESS;
        }

        // Enforce the same unlock gating the read path computes: a node that is still
        // locked (prerequisite/stage/parent not satisfied) cannot be advanced. This keeps
        // the write path from completing nodes out of order and feeding bad auto-completion.
        if ((newStatus == RoadmapStepStatus.IN_PROGRESS || newStatus == RoadmapStepStatus.COMPLETED)
                && !careerNodes.isEmpty()) {
            Map<UUID, StudentProgress> progressByNode = mapProgressByNodeId(
                    studentProgressRepository.findByStudent_UserIdAndSkillNode_NodeIdIn(
                            student.getUserId(),
                            gateNodes.stream().map(SkillNode::getNodeId).toList()));
            // Same resolver as the read path, or the student could be refused a node
            // the roadmap had just shown them as unlocked.
            SelectionView gateSelectionView = roadmapSelectionResolver.resolve(student.getUserId(), gateNodes);
            ResolvedOrder order = roadmapEdgeResolver.resolve(gateNodes,
                    studentRoadmapContext(student, node.getCareerRole(),
                            marketDemandSafe(node.getCareerRole() == null
                                    ? null : node.getCareerRole().getCareerId())),
                    gateSelectionView);
            String computedStatus =
                    buildFrontendStatusMap(gateNodes, progressByNode, gateSelectionView, order).get(node.getNodeId());
            if (FRONTEND_LOCKED_STATUS.equals(computedStatus)) {
                throw new ForbiddenException("This node is locked; complete its prerequisites first.");
            }
        }

        progress.setStatus(newStatus);
        // Leaving COMPLETED has to drop the date with it. The stamp is guarded by
        // `getCompletedAt() == null`, so a node taken back to in-progress and then
        // completed again would keep the first date forever — the card would show
        // "06 Aug 2026" under work finished in September. Nothing could un-complete
        // a node from the canvas before, so the path was unreachable; the status bar
        // on the card makes it a click.
        if (RoadmapStepStatus.COMPLETED != newStatus) {
            progress.setCompletedAt(null);
        }
        if (RoadmapStepStatus.COMPLETED == newStatus
                && progress.getCompletedAt() == null) {
            progress.setCompletedAt(java.time.LocalDateTime.now());
            
            // Auto-sync skill to profile (only when the student has a target career)
            if (node.getSkill() != null
                    && student.getCareerRole() != null
                    && student.getCareerRole().getCareerId() != null) {
                Skill skill = skillRepository.findById(node.getSkill().getSkillId()).orElse(null);
                if (skill != null) {
                    boolean hasSkill = studentSkillRepository.existsByStudent_UserIdAndSkill_SkillId(student.getUserId(), skill.getSkillId());
                    if (!hasSkill) {
                        List<SkillNode> allNodesForSkill = skillNodeRepository
                                .findBySkill_SkillIdAndCareerRole_CareerId(
                                        skill.getSkillId(),
                                        student.getCareerRole().getCareerId()
                                );
                        boolean allCompleted = true;
                        
                        for (SkillNode skillNode : allNodesForSkill) {
                            if (skillNode.getNodeId().equals(node.getNodeId())) {
                                continue;
                            }
                            StudentProgress nodeProgress = studentProgressRepository.findByStudent_UserIdAndSkillNode_NodeId(student.getUserId(), skillNode.getNodeId()).orElse(null);
                            if (nodeProgress == null || nodeProgress.getStatus() != RoadmapStepStatus.COMPLETED) {
                                allCompleted = false;
                                break;
                            }
                        }

                        if (allCompleted) {
                            StudentSkill newSkill = StudentSkill.builder()
                                    .student(Student.builder().userId(student.getUserId()).build())
                                    .skill(skill)
                                    .build();
                            studentSkillRepository.save(newSkill);
                            log.info("RoadmapServiceImpl: Auto-synced skill {} to student {} after all required nodes were completed", skill.getSkillName(), student.getUserId());
                        }
                    }
                }
            }
        }
        studentProgressRepository.save(progress);

        // A topic (spine) node auto-completes from its children, so whenever a child's
        // status changes, re-evaluate every ancestor topic and complete/revert it.
        syncTopicAutoCompletion(student, node, selectionView);
    }

    /**
     * Walks up the parent chain of {@code changedNode} and keeps each topic node's
     * stored progress in sync with the auto-completion rule: a topic is COMPLETED once
     * its child weight crosses {@link #parentCompletionThreshold}, and reverted back to
     * IN_PROGRESS if a child is later un-completed and drags it below the threshold.
     */
    private void syncTopicAutoCompletion(Student student, SkillNode changedNode, SelectionView selectionView) {
        SkillNode topic = changedNode.getParentNode();
        while (topic != null) {
            final SkillNode currentTopic = topic;
            List<SkillNode> children = skillNodeRepository.findByParentNode_NodeId(currentTopic.getNodeId());
            long totalWeight = 0;
            long doneWeight = 0;
            for (SkillNode child : children) {
                // Off-path alternatives never count toward a topic's completion, so
                // e.g. "Pick a Language" completes from the single chosen language.
                if (selectionView.isExcludedFromProgress(child.getNodeId())) {
                    continue;
                }
                int weight = nodeWeight(child);
                totalWeight += weight;
                StudentProgress childProgress = studentProgressRepository
                        .findByStudent_UserIdAndSkillNode_NodeId(student.getUserId(), child.getNodeId())
                        .orElse(null);
                if (childProgress != null && RoadmapStepStatus.COMPLETED == childProgress.getStatus()) {
                    doneWeight += weight;
                }
            }
            boolean reachedThreshold = totalWeight > 0
                    && (double) doneWeight / totalWeight >= parentCompletionThreshold;

            StudentProgress topicProgress = studentProgressRepository
                    .findByStudent_UserIdAndSkillNode_NodeId(student.getUserId(), currentTopic.getNodeId())
                    .orElse(null);

            if (reachedThreshold) {
                if (topicProgress == null) {
                    topicProgress = StudentProgress.builder()
                            .student(Student.builder().userId(student.getUserId()).build())
                            .skillNode(currentTopic)
                            .createdAt(LocalDateTime.now())
                            .build();
                }
                if (RoadmapStepStatus.COMPLETED != topicProgress.getStatus()) {
                    topicProgress.setStatus(RoadmapStepStatus.COMPLETED);
                    topicProgress.setCompletedAt(LocalDateTime.now());
                    studentProgressRepository.save(topicProgress);
                    log.info("RoadmapServiceImpl: Topic {} auto-completed for student {} ({}/{} child weight)",
                            currentTopic.getNodeName(), student.getUserId(), doneWeight, totalWeight);
                }
            } else if (topicProgress != null && RoadmapStepStatus.COMPLETED == topicProgress.getStatus()) {
                // Dropped below the threshold: remove the auto-completion so the read
                // path recomputes the topic's real status (locked/current) from scratch.
                studentProgressRepository.delete(topicProgress);
                log.info("RoadmapServiceImpl: Topic {} auto-completion cleared for student {} ({}/{} child weight)",
                        currentTopic.getNodeName(), student.getUserId(), doneWeight, totalWeight);
            }

            topic = topic.getParentNode();
        }
    }

    /**
     * Retrieves the student entity from the provided authorization header.
     *
     * @return the {@link Student} associated with the token
     * @throws RuntimeException if the authorization header is invalid
     * @throws ResourceNotFoundException if the user or student profile cannot be found
     */
    private Student getStudentFromContext() {
        String email = SecurityUtils.getCurrentUserEmail();
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new ResourceNotFoundException("User not found");
        }

        Student student = studentRepository.findByUserId(user.getUserId());
        if (student == null) {
            throw new ResourceNotFoundException("Student profile not found");
        }
        return student;
    }

    /**
     * Maps a {@link SkillNode} to a legacy {@link RoadmapNodeResponse}.
     *
     * @param node the skill node to map
     * @param status the progress status of the node, defaults to "not_started" if null
     * @return the mapped {@link RoadmapNodeResponse}
     */
    private RoadmapNodeResponse mapToLegacyNodeDto(SkillNode node, String status) {
        String parentNode = node.getParentNode() != null ?
                node.getParentNode().getNodeName() : null;

        return RoadmapNodeResponse.builder()
                .nodeId(node.getNodeId())
                .nodeName(node.getNodeName())
                .parentNode(parentNode)
                .nodeLevel(node.getNodeLevel())
                .description(node.getDescription())
                .resource(node.getResource())
                .status(status != null ? status : "not_started")
                .build();
    }

    /**
     * Maps a list of student progress records by their associated node IDs.
     *
     * @param progresses the list of {@link StudentProgress}
     * @return a map of node IDs to their corresponding {@link StudentProgress}
     */
    private Map<UUID, StudentProgress> mapProgressByNodeId(List<StudentProgress> progresses) {
        Map<UUID, StudentProgress> progressByNodeId = new HashMap<>();
        for (StudentProgress progress : progresses) {
            if (progress.getSkillNode().getNodeId() != null) {
                progressByNodeId.put(progress.getSkillNode().getNodeId(), progress);
            }
        }
        return progressByNodeId;
    }

    /**
     * Builds a map of frontend status strings for each node based on the student's progress.
     * Determines whether a node is completed, in progress, current, or locked based on prerequisites.
     *
     * @param nodes the list of all {@link SkillNode}s in the roadmap
     * @param progressByNodeId the mapped student progress by node ID
     * @return a map of node IDs to their frontend status string
     */
    private static final String NEVER_COMPLETE_POLICY = "NEVER_COMPLETE";

    private Map<UUID, String> buildFrontendStatusMap(
            List<SkillNode> nodes,
            Map<UUID, StudentProgress> progressByNodeId,
            SelectionView selectionView,
            ResolvedOrder order
    ) {
        Map<UUID, String> statusByNodeId = new HashMap<>();
        Set<String> passedStages = findPassedStages(nodes, progressByNodeId);
        Set<UUID> topicIds = topicParentIds(nodes);
        Map<UUID, List<SkillNode>> childrenByParent = childrenByParent(nodes);
        // A completed node can come from assessment/evidence and may sit ahead
        // of unfinished prerequisites. It remains visibly completed, but it may
        // advance the learning path only when the path actually reached it.
        Set<UUID> reachedByPath = new HashSet<>();

        // Parents/previous nodes must be classified before their dependants. The
        // resolver's visitOrder already guarantees that for the computed chain;
        // walking `nodes` in database order instead would leave a predecessor
        // unclassified, and an unknown gate reads as locked — which would lock
        // everything behind it.
        for (SkillNode node : orderDependenciesFirst(nodes, order)) {
            // Unchosen alternative of a decided CHOOSE_ONE group (and its subtree):
            // greyed out, off the active path — bypass the normal gating rules.
            if (selectionView.isGreyedAlternative(node.getNodeId())) {
                statusByNodeId.put(node.getNodeId(), FRONTEND_ALTERNATIVE_STATUS);
                continue;
            }

            boolean topic = topicIds.contains(node.getNodeId());
            SkillNode previous = previousOf(node, order, nodes);
            boolean sequentialLocked = isSequentialGateLocked(previous, statusByNodeId)
                    || (previous != null && !reachedByPath.contains(previous.getNodeId()));
            boolean gateLocked = isStageLocked(node, passedStages)
                    || sequentialLocked
                    || (!topic && isParentReachedGateLocked(node.getParentNode(), statusByNodeId))
                    || (!topic && node.getParentNode() != null
                        && !reachedByPath.contains(node.getParentNode().getNodeId()));

            StudentProgress progress = progressByNodeId.get(node.getNodeId());
            if (progress != null && RoadmapStepStatus.COMPLETED == progress.getStatus()) {
                statusByNodeId.put(node.getNodeId(), FRONTEND_COMPLETED_STATUS);
                if (!gateLocked) {
                    reachedByPath.add(node.getNodeId());
                }
                continue;
            }
            if (progress != null && RoadmapStepStatus.IN_PROGRESS == progress.getStatus()) {
                statusByNodeId.put(node.getNodeId(), FRONTEND_IN_PROGRESS_STATUS);
                if (!gateLocked) {
                    reachedByPath.add(node.getNodeId());
                }
                continue;
            }

            if (topic) {
                // A topic (spine) node is gated only by its own sequential order
                // (previousNode) and stage — never by its own children. Once
                // reachable it auto-completes as soon as enough child weight is done,
                // otherwise it stays the current focus while its sub-skills are learned.
                if (gateLocked) {
                    statusByNodeId.put(node.getNodeId(), FRONTEND_LOCKED_STATUS);
                } else if (childCompletionRatio(node, childrenByParent, progressByNodeId, selectionView) >= parentCompletionThreshold) {
                    statusByNodeId.put(node.getNodeId(), FRONTEND_COMPLETED_STATUS);
                    reachedByPath.add(node.getNodeId());
                } else {
                    statusByNodeId.put(node.getNodeId(), FRONTEND_CURRENT_STATUS);
                    reachedByPath.add(node.getNodeId());
                }
                continue;
            }

            // Leaf / child node: unlocks as soon as its parent topic is REACHED
            // (i.e. no longer locked) rather than fully completed, plus any
            // sequential previousNode ordering among siblings/spine leaves.
            statusByNodeId.put(node.getNodeId(), gateLocked ? FRONTEND_LOCKED_STATUS : FRONTEND_CURRENT_STATUS);
            if (!gateLocked) {
                reachedByPath.add(node.getNodeId());
            }
        }

        return statusByNodeId;
    }

    /** How far the node sits from a root; the cap keeps a cycle in the data from hanging the request. */
    /**
     * Depth from the stored column, walking the parent chain only as a fallback.
     *
     * <p>The column is authoritative because every consumer has to agree on it —
     * the visibility filter, the layout and the client each derived their own
     * answer before, and the client's disagreement is what put 134 of 169 nodes
     * in a single column. The walk survives for rows written before the
     * standardisation, and for tests that build nodes by hand.
     */
    private int depthOf(SkillNode node) {
        if (node.getDepth() != null) {
            return node.getDepth();
        }
        int depth = 0;
        for (SkillNode cursor = node.getParentNode(); cursor != null && depth < 32;
             cursor = cursor.getParentNode()) {
            depth++;
        }
        return depth;
    }

    /** Node ids that are referenced as a {@code parentNode} by at least one other node (i.e. topics). */
    private Set<UUID> topicParentIds(List<SkillNode> nodes) {
        return nodes.stream()
                .map(SkillNode::getParentNode)
                .filter(Objects::nonNull)
                .map(SkillNode::getNodeId)
                .collect(Collectors.toSet());
    }

    /** Children grouped by their parent topic id. */
    private Map<UUID, List<SkillNode>> childrenByParent(List<SkillNode> nodes) {
        return nodes.stream()
                .filter(n -> n.getParentNode() != null)
                .collect(Collectors.groupingBy(n -> n.getParentNode().getNodeId()));
    }

    /**
     * Completed-child weight over total-child weight for a topic (0.0 when it has no
     * counted children). Children off the active path (unchosen alternatives, or all
     * alternatives while a CHOOSE_ONE group is undecided) are excluded from both sides,
     * so e.g. "Pick a Language" completes once the single chosen language is done.
     */
    private double childCompletionRatio(
            SkillNode topic,
            Map<UUID, List<SkillNode>> childrenByParent,
            Map<UUID, StudentProgress> progressByNodeId,
            SelectionView selectionView
    ) {
        List<SkillNode> children = childrenByParent.getOrDefault(topic.getNodeId(), List.of());
        long totalWeight = 0;
        long doneWeight = 0;
        for (SkillNode child : children) {
            if (selectionView.isExcludedFromProgress(child.getNodeId())) {
                continue;
            }
            int weight = nodeWeight(child);
            totalWeight += weight;
            StudentProgress p = progressByNodeId.get(child.getNodeId());
            if (p != null && RoadmapStepStatus.COMPLETED == p.getStatus()) {
                doneWeight += weight;
            }
        }
        return totalWeight == 0 ? 0.0 : (double) doneWeight / totalWeight;
    }

    private int nodeWeight(SkillNode node) {
        if (node.getType() != null && node.getType().getWeight() != null && node.getType().getWeight() > 0) {
            return node.getType().getWeight();
        }
        return 1;
    }

    /**
     * Sequential gate (spine order / sibling chaining): the dependant is locked
     * until its gate node is COMPLETED. A NEVER_COMPLETE group header can never be
     * completed, so there the dependant only stays locked while the header is locked.
     */
    private boolean isSequentialGateLocked(SkillNode gate, Map<UUID, String> statusByNodeId) {
        if (gate == null) {
            return false;
        }
        String gateStatus = statusByNodeId.get(gate.getNodeId());
        if (gateStatus == null) {
            return true;
        }
        if (NEVER_COMPLETE_POLICY.equalsIgnoreCase(gate.getCompletionPolicy())) {
            return FRONTEND_LOCKED_STATUS.equals(gateStatus);
        }
        return !FRONTEND_COMPLETED_STATUS.equals(gateStatus);
    }

    /**
     * Hierarchy gate (topic -> its children): a child unlocks the moment its parent
     * topic is reached, so it is locked only while the parent itself is still locked
     * (or unknown). This lets a student start learning sub-skills as soon as the topic
     * is the current focus, instead of forcing the topic to be completed first.
     */
    private boolean isParentReachedGateLocked(SkillNode parent, Map<UUID, String> statusByNodeId) {
        if (parent == null) {
            return false;
        }
        String parentStatus = statusByNodeId.get(parent.getNodeId());
        if (parentStatus == null) {
            return true;
        }
        return FRONTEND_LOCKED_STATUS.equals(parentStatus);
    }

    /**
     * A node whose NodeType requires unlock keys stays locked until every stage
     * named in stageUnlockKey has been passed (all of that stage's completable
     * nodes are COMPLETED).
     */
    private boolean isStageLocked(SkillNode node, Set<String> passedStages) {
        if (node.getType() == null || !Boolean.TRUE.equals(node.getType().getUnlockKeyRequired())) {
            return false;
        }
        List<String> requiredStages = node.getType().getStageUnlockKey();
        if (requiredStages == null) {
            return false;
        }
        for (String stage : requiredStages) {
            if (stage != null && !passedStages.contains(stage.trim().toUpperCase())) {
                return true;
            }
        }
        return false;
    }

    /** Stages where every completable (non-group) node is COMPLETED. */
    private Set<String> findPassedStages(List<SkillNode> nodes, Map<UUID, StudentProgress> progressByNodeId) {
        Map<String, Boolean> stageCompleted = new HashMap<>();
        for (SkillNode node : nodes) {
            if (node.getType() == null || node.getType().getStage() == null) {
                continue;
            }
            String stage = node.getType().getStage().name();
            if (NEVER_COMPLETE_POLICY.equalsIgnoreCase(node.getCompletionPolicy())) {
                stageCompleted.putIfAbsent(stage, true);
                continue;
            }
            StudentProgress progress = progressByNodeId.get(node.getNodeId());
            boolean completed = progress != null && RoadmapStepStatus.COMPLETED == progress.getStatus();
            stageCompleted.merge(stage, completed, Boolean::logicalAnd);
        }

        Set<String> passed = new HashSet<>();
        stageCompleted.forEach((stage, completed) -> {
            if (Boolean.TRUE.equals(completed)) {
                passed.add(stage);
            }
        });
        return passed;
    }

    /**
     * The node that must be completed before {@code node}, for this student.
     *
     * <p>Reads the resolver rather than the {@code previous_node} column so the
     * unlock chain is the same chain the student is shown. Falls back to the
     * column only when the resolver produced nothing for this roadmap, which
     * keeps the older callers (career template, progress-only paths) working.
     */
    private SkillNode previousOf(SkillNode node, ResolvedOrder order, List<SkillNode> nodes) {
        if (order == null) {
            return node.getPreviousNode();
        }
        UUID previousId = order.previousByNodeId().get(node.getNodeId());
        if (previousId == null) {
            return null;
        }
        for (SkillNode candidate : nodes) {
            if (previousId.equals(candidate.getNodeId())) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Kahn-style ordering over parent references and the student's computed
     * predecessors; tolerates cycles by appending leftovers.
     *
     * <p>{@code order.visitOrder()} is already dependencies-first, so it is used
     * directly when available. The Kahn pass remains as the fallback for callers
     * that have no resolved order — and as a safety net, since an incomplete
     * visitOrder would silently lock every node it omitted.
     */
    private List<SkillNode> orderDependenciesFirst(List<SkillNode> nodes, ResolvedOrder order) {
        if (order != null && order.visitOrder().size() == nodes.size()) {
            Map<UUID, SkillNode> byId = new HashMap<>();
            for (SkillNode node : nodes) {
                byId.put(node.getNodeId(), node);
            }
            List<SkillNode> ordered = new ArrayList<>(nodes.size());
            for (UUID nodeId : order.visitOrder()) {
                SkillNode node = byId.get(nodeId);
                if (node != null) {
                    ordered.add(node);
                }
            }
            if (ordered.size() == nodes.size()) {
                return ordered;
            }
        }

        Set<UUID> emitted = new HashSet<>();
        List<SkillNode> ordered = new ArrayList<>(nodes.size());
        List<SkillNode> remaining = new ArrayList<>(nodes);

        boolean progressMade = true;
        while (!remaining.isEmpty() && progressMade) {
            progressMade = false;
            Iterator<SkillNode> iterator = remaining.iterator();
            while (iterator.hasNext()) {
                SkillNode node = iterator.next();
                SkillNode previous = previousOf(node, order, nodes);
                boolean parentReady = node.getParentNode() == null || emitted.contains(node.getParentNode().getNodeId());
                boolean previousReady = previous == null || emitted.contains(previous.getNodeId());
                if (parentReady && previousReady) {
                    ordered.add(node);
                    emitted.add(node.getNodeId());
                    iterator.remove();
                    progressMade = true;
                }
            }
        }
        ordered.addAll(remaining);
        return ordered;
    }

    /**
     * Calculates the overall progress percentage for a roadmap.
     *
     * @param nodes the list of {@link SkillNode}s in the roadmap
     * @param progressByNodeId the mapped student progress by node ID
     * @return the calculated progress percentage (0 to 100)
     */
    private int calculateProgress(List<SkillNode> nodes, Map<UUID, StudentProgress> progressByNodeId) {
        return roadmapProgressCalculator.calculateProgress(nodes, progressByNodeId);
    }


    /**
     * Market demand keyed by skill id, or an empty map if it cannot be produced.
     *
     * <p>Demand is decoration on the roadmap, not part of it: a student must still
     * be able to see their path when the scraper has never run, when the trend
     * table is empty, or when that query fails outright. Swallowing the failure
     * here keeps a reporting concern from taking down the page it annotates.
     */
    private Map<UUID, SkillDemandResponse> marketDemandSafe(UUID careerId) {
        try {
            return marketDemandService.demandBySkill(careerId);
        } catch (Exception e) {
            log.warn("RoadmapServiceImpl: market demand unavailable, rendering roadmap "
                    + "without it: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * @param fptAccount when false the FLM overlay is skipped entirely, so nodes carry no
     *                   fptCoverage/fptResources and the two overlay queries never run
     */
    /**
     * The student's proven skills, keyed by skill id.
     *
     * <p>Fed to the node view so a node can say where the student stands against
     * the bar it sets. {@code requiredProficiency} on its own is unreadable:
     * "needs APPLIED" means nothing until you know you are at PRACTICED.
     */
    private Map<UUID, StudentSkill> heldSkillsBySkillId(UUID studentId) {
        Map<UUID, StudentSkill> held = new HashMap<>();
        for (StudentSkill studentSkill : studentSkillRepository.findByStudent_UserId(studentId)) {
            if (studentSkill.getSkill() != null) {
                held.put(studentSkill.getSkill().getSkillId(), studentSkill);
            }
        }
        return held;
    }

    /**
     * How this node finishes, said plainly.
     *
     * <p>A topic auto-completes once enough of its sub-skills are done, and that
     * used to happen with nothing on screen having warned it would, or shown what
     * was still outstanding.
     */
    private String completionRuleFor(SkillNode node, boolean isTopic, int childTotal, int childCompleted) {
        if (isTopic && childTotal > 0) {
            return String.format("Completes at %d%% of its sub-skills (%d of %d done)",
                    Math.round(parentCompletionThreshold * 100), childCompleted, childTotal);
        }
        if (node.getRequiredProficiency() != null && node.getRequiredProficiency() > 0) {
            return "Completes when you reach " + proficiencyName(node.getRequiredProficiency());
        }
        return "Completes when you mark it done";
    }

    private String proficiencyName(Integer level) {
        if (level == null) {
            return "no set level";
        }
        return switch (level) {
            case 1 -> "AWARE";
            case 2 -> "PRACTICED";
            case 3 -> "APPLIED";
            case 4 -> "PROFESSIONAL";
            default -> "level " + level;
        };
    }

    /**
     * Career readiness for this student, or nothing at all if it cannot be had.
     *
     * <p>Readiness is deliberately not {@code progress}: progress counts nodes
     * ticked off on the view in front of the student, readiness counts the
     * career's essential skills they actually hold. Finishing every node on a
     * sub-roadmap moves the first to 100% and the second barely at all, and the
     * student is entitled to see both numbers rather than the flattering one.
     *
     * <p>Never allowed to break the roadmap: a failure here costs the two
     * percentages, not the page.
     */
    private SeniorityCalculator.SeniorityVerdict readinessSafe(Student student, CareerRole careerRole) {
        if (student == null || student.getUserId() == null
                || careerRole == null || careerRole.getCareerId() == null) {
            return null;
        }
        try {
            return seniorityCalculator.compute(student.getUserId(), careerRole.getCareerId());
        } catch (Exception e) {
            log.warn("RoadmapServiceImpl: could not compute readiness for user {}: {}",
                    student.getUserId(), e.getMessage());
            return null;
        }
    }

    private StudentRoadmapResponse.StudentRoadmapResponseBuilder withReadiness(
            StudentRoadmapResponse.StudentRoadmapResponseBuilder builder,
            SeniorityCalculator.SeniorityVerdict verdict) {
        if (verdict == null) {
            return builder;
        }
        return builder
                .readiness(verdict.ratioAll() == null ? null : verdict.ratioAll().doubleValue())
                .readinessVerified(verdict.ratioVerified() == null ? null : verdict.ratioVerified().doubleValue())
                .readinessRequiredCount(verdict.requiredCount())
                .readinessHeldCount(verdict.heldCount())
                .readinessVerifiedCount(verdict.verifiedCount());
    }

    /**
     * The readiness denominator as rows, for the skill map.
     *
     * <p>Same failure policy as {@link #readinessSafe}: losing this costs the map,
     * not the roadmap.
     */
    private List<CoreSkillResponse> coreSkillsSafe(Student student, CareerRole careerRole,
                                                   Map<UUID, SkillDemandResponse> demandBySkill) {
        if (student == null || student.getUserId() == null
                || careerRole == null || careerRole.getCareerId() == null) {
            return null;
        }
        try {
            Map<UUID, SkillDemandResponse> demand = demandBySkill != null ? demandBySkill : Map.of();
            return seniorityCalculator.coreSkills(student.getUserId(), careerRole.getCareerId()).stream()
                    .map(core -> CoreSkillResponse.builder()
                            .skillId(core.skillId())
                            .skillName(core.skillName())
                            .importance(core.importance())
                            .proficiency(core.proficiency())
                            .verifiedBy(core.verifiedBy())
                            .marketDemand(demand.get(core.skillId()))
                            .build())
                    .toList();
        } catch (Exception e) {
            log.warn("RoadmapServiceImpl: could not build the core skill set for user {}: {}",
                    student.getUserId(), e.getMessage());
            return null;
        }
    }

    private List<RoadmapNodeResponse> buildRoadmapTree(
            List<SkillNode> nodes,
            Map<UUID, String> statusByNodeId,
            Map<UUID, StudentProgress> progressByNodeId,
            SelectionView selectionView,
            boolean fptAccount,
            ResolvedOrder order,
            Map<UUID, SkillDemandResponse> demandBySkill,
            Map<UUID, Integer> hiddenChildren,
            List<SkillNode> allNodes,
            Map<UUID, StudentSkill> heldSkillsBySkillId,
            Map<UUID, Integer> subtreeSizes,
            Set<UUID> enterableIds,
            StudentRoadmapContext context,
            UUID studentId
    ) {
        // Presentation-only placement, joined in from the layout table.
        Map<UUID, RoadmapNodeLayout> layoutsByNodeId = new HashMap<>();
        for (RoadmapNodeLayout layout : roadmapNodeLayoutRepository
                .findByNodeIdIn(nodes.stream().map(SkillNode::getNodeId).toList())) {
            layoutsByNodeId.put(layout.getNodeId(), layout);
        }

        // Counted over EVERY node of the career, not the slice being drawn: a topic
        // that reads "2/12" must keep reading 2/12 when ten of those children are
        // held back by the visibility filter.
        Set<UUID> topicIds = topicParentIds(allNodes);
        Map<UUID, List<SkillNode>> childrenByParent = childrenByParent(allNodes);

        // FLM overlay: skill name -> teaching FPT subjects, and subject -> lesson resources.
        // Left empty for non-FPT accounts, which also skips both overlay queries.
        Map<String, List<FptSubject>> subjectsBySkill = new HashMap<>();
        Map<String, List<FptSubjectResource>> resourcesByCode = new HashMap<>();
        List<FptSubjectSkill> fptLinks = fptAccount ? fptSubjectSkillRepository.findAll() : List.of();
        if (!fptLinks.isEmpty()) {
            Set<String> codes = fptLinks.stream().map(FptSubjectSkill::getSubjectCode).collect(Collectors.toSet());
            Map<String, FptSubject> subjectByCode = fptSubjectRepository.findAllById(codes).stream()
                    .collect(Collectors.toMap(FptSubject::getCode, s -> s));
            for (FptSubjectSkill link : fptLinks) {
                FptSubject subject = subjectByCode.get(link.getSubjectCode());
                if (subject != null && link.getSkillName() != null) {
                    subjectsBySkill.computeIfAbsent(link.getSkillName().toLowerCase(), k -> new ArrayList<>()).add(subject);
                }
            }
            resourcesByCode = fptSubjectResourceRepository
                    .findBySubjectCodeInOrderBySubjectCodeAscOrderIndexAsc(codes).stream()
                    .collect(Collectors.groupingBy(FptSubjectResource::getSubjectCode));
        }
        final Map<String, List<FptSubject>> subjectsBySkillFinal = subjectsBySkill;
        final Map<String, List<FptSubjectResource>> resourcesByCodeFinal = resourcesByCode;

        // Market demand for the catalog skill behind each node, resolved once by the
        // caller and shared with the ordering pass so the number the student reads on
        // a node is the same one that decided where the node sits.
        final Map<UUID, SkillDemandResponse> demand = demandBySkill != null ? demandBySkill : Map.of();
        List<StudentSkillEvidence> activeEvidence = studentId == null ? List.of()
                : studentSkillEvidenceRepository.findByUserIdAndStatusIn(studentId,
                        List.of(com.inteliroadmap.backend.domain.enums.EvidenceStatus.ACCEPTED,
                                com.inteliroadmap.backend.domain.enums.EvidenceStatus.PENDING));
        final int relativeDepthBase = nodes.stream().mapToInt(this::depthOf).min().orElse(0);

        // Emit in the student's own order, not the database's. The client lays each
        // topic's children out in the order this array gives them, so sending them
        // in `node_level, node_name` order would render the static roadmap no
        // matter what the resolver decided — the ordering would exist only on the
        // wire and never on the screen.
        return orderDependenciesFirst(nodes, order).stream()
                .map(node -> {
                    RoadmapNodeLayout layout = layoutsByNodeId.get(node.getNodeId());
                    boolean isTopic = topicIds.contains(node.getNodeId());
                    List<SkillNode> children = isTopic
                            ? childrenByParent.getOrDefault(node.getNodeId(), List.of())
                            : List.of();
                    // Only children on the student's active path count toward the topic
                    // bar. For a CHOOSE_ONE group that means the single chosen alternative,
                    // so a picked-and-finished "Pick a Database" reads 1/1, not 1/6.
                    List<SkillNode> countedChildren = children.stream()
                            .filter(c -> !selectionView.isExcludedFromProgress(c.getNodeId()))
                            .toList();
                    int childCompleted = (int) countedChildren.stream()
                            .map(c -> progressByNodeId.get(c.getNodeId()))
                            .filter(p -> p != null && RoadmapStepStatus.COMPLETED == p.getStatus())
                            .count();
                    StudentProgress nodeProgress = progressByNodeId.get(node.getNodeId());
                    String completedAt = nodeProgress != null && nodeProgress.getCompletedAt() != null
                            ? nodeProgress.getCompletedAt().toString()
                            : null;

                    // FLM coverage/resources for this node, joined by its skill name.
                    String skillName = node.getSkill() != null && node.getSkill().getSkillName() != null
                            ? node.getSkill().getSkillName() : node.getNodeName();
                    List<FptSubject> coverSubjects = skillName != null
                            ? subjectsBySkillFinal.getOrDefault(skillName.toLowerCase(), List.of())
                            : List.of();
                    boolean covered = !coverSubjects.isEmpty();
                    // Only flag self-study on concrete leaf skill nodes, so the canvas isn't noisy.
                    boolean selfStudy = !covered && node.getSkill() != null && !isTopic;
                    // Non-FPT accounts get null, not a selfStudy=true stub: with an empty
                    // overlay every leaf would otherwise claim FPT doesn't teach it.
                    FptNodeCoverageResponse fptCoverage = (fptAccount && (covered || selfStudy))
                            ? FptNodeCoverageResponse.builder()
                                    .covered(covered)
                                    .selfStudy(selfStudy)
                                    .subjects(coverSubjects.stream()
                                            // Term is per-curriculum now; omit it on the shared node view.
                                            .map(s -> FptSubjectRefResponse.builder()
                                                    .code(s.getCode()).name(s.getName()).build())
                                            .toList())
                                    .build()
                            : null;
                    List<FptNodeResourceResponse> fptResources = fptAccount
                            ? buildNodeResources(coverSubjects, resourcesByCodeFinal)
                            : null;

                    StudentSkill heldSkill = (node.getSkill() == null || heldSkillsBySkillId == null)
                            ? null : heldSkillsBySkillId.get(node.getSkill().getSkillId());
                    List<StudentSkillEvidence> matchingEvidence = activeEvidence.stream()
                            .filter(e -> evidenceSupportsNode(e, node))
                            .sorted(Comparator.comparing(StudentSkillEvidence::getConfidence,
                                    Comparator.nullsLast(Comparator.reverseOrder())))
                            .toList();
                    double evidenceThreshold = effectiveEvidenceThreshold(node, context);
                    List<RoadmapNodeEvidenceResponse> evidenceDto = matchingEvidence.stream()
                            .map(e -> RoadmapNodeEvidenceResponse.builder()
                                    .evidenceId(e.getEvidenceId())
                                    .skillName(e.getSkillName())
                                    .sourceType(e.getSourceType() == null ? null : e.getSourceType().name())
                                    .sourceUrl(e.getSourceUrl())
                                    .confidence(e.getConfidence() == null ? null : e.getConfidence().doubleValue())
                                    .status(e.getStatus() == null ? null : e.getStatus().name())
                                    .detectedBy(e.getDetectedBy())
                                    .build())
                            .toList();
                    RoadmapEdgeResolver.NodePriority priority =
                            roadmapEdgeResolver.priorityOf(node, context);
                    String semanticType = node.getSemanticType() == null
                            ? (isTopic ? "TOPIC" : node.getSkill() != null ? "SKILL" : "CAPABILITY")
                            : node.getSemanticType();
                    RoadmapTopicResponse topicDto = "TOPIC".equals(semanticType)
                            || "CHECKPOINT".equals(semanticType)
                            ? new RoadmapTopicResponse(countedChildren.size(), childCompleted,
                                    hiddenChildren == null ? null : hiddenChildren.get(node.getNodeId()),
                                    completionRuleFor(node, isTopic, countedChildren.size(), childCompleted))
                            : null;
                    RoadmapSkillResponse skillDto = "SKILL".equals(semanticType) && node.getSkill() != null
                            ? new RoadmapSkillResponse(node.getSkill().getSkillId(), node.getSkill().getSkillName(),
                                    node.getSkill().getCategory(), node.getRequiredProficiency(),
                                    heldSkill == null ? null : heldSkill.getProficiency(),
                                    heldSkill == null ? null : heldSkill.getVerifiedBy(),
                                    demand.get(node.getSkill().getSkillId()))
                            : null;
                    return RoadmapNodeResponse.builder()
                            .nodeId(node.getNodeId())
                            .nodeName(node.getNodeName())
                            .semanticType(semanticType)
                            .relativeDepth(Math.max(0, depthOf(node) - relativeDepthBase))
                            .topic(topicDto)
                            .skill(skillDto)
                            .parentNode(node.getParentNode() != null ? node.getParentNode().getNodeId().toString() : null)
                            // From the resolver, not the column: a client that still
                            // derives its own edges from this field gets the
                            // personalised order without shipping a new build.
                            .previousNode(order != null && order.previousByNodeId().get(node.getNodeId()) != null
                                    ? order.previousByNodeId().get(node.getNodeId()).toString()
                                    : null)
                            .nodeLevel(node.getNodeLevel())
                            .stage(node.getType() != null && node.getType().getStage() != null
                                    ? node.getType().getStage().name() : null)
                            .completionPolicy(node.getCompletionPolicy())
                            .weight(node.getType() != null ? node.getType().getWeight() : null)
                            .requiredProficiency(node.getRequiredProficiency())
                            .currentProficiency(heldSkill == null ? null : heldSkill.getProficiency())
                            // Null is not a failure, it is a weaker claim: the
                            // student said so themselves. The card is expected to
                            // show the difference rather than flatten it.
                            .proficiencyVerifiedBy(heldSkill == null ? null : heldSkill.getVerifiedBy())
                            .completionRule(completionRuleFor(node, isTopic, countedChildren.size(), childCompleted))
                            .evidence(evidenceDto)
                            .evidenceRequiredConfidence(isTopic ? null : evidenceThreshold)
                            .evidenceDecision(evidenceDecision(node, isTopic, countedChildren.size(), childCompleted,
                                    matchingEvidence, evidenceThreshold,
                                    statusByNodeId.getOrDefault(node.getNodeId(), FRONTEND_LOCKED_STATUS)))
                            .subtreeSize(subtreeSizes == null ? null : subtreeSizes.get(node.getNodeId()))
                            .entersRoadmap(enterableIds != null && enterableIds.contains(node.getNodeId()))
                            .parentTopic(isTopic)
                            .depth(depthOf(node))
                            .hiddenChildren(hiddenChildren == null
                                    ? null : hiddenChildren.get(node.getNodeId()))
                            .childTotal(countedChildren.size())
                            .childCompleted(childCompleted)
                            .selection(node.getSelection())
                            .chooseCount(node.getChooseCount())
                            .nodeKind(node.getNodeKind())
                            .tier(node.getTier())
                            .tierLocked(roadmapTierResolver.isLocked(node,
                                    roadmapTierResolver.ceilingFor(
                                            context == null || context.level() == null
                                                    ? null : context.level().name())))
                            .axis(node.getAxis())
                            .isOptional(node.getIsOptional())
                            .isCheckpoint(node.getIsCheckpoint())
                            .positionX(layout != null ? layout.getPositionX() : null)
                            .positionY(layout != null ? layout.getPositionY() : null)
                            .lane(layout != null ? layout.getLane() : null)
                            .displayOrder(layout != null ? layout.getDisplayOrder() : null)
                            .description(node.getDescription())
                            .resource(node.getResource())
                            .difficulty(node.getDifficulty())
                            // Deprecated and empty on every row; copied through rather than
                            // dropped so the response shape does not change under any client.
                            .estimatedHours(node.getEstimatedHours())
                            .objectives(node.getObjectives())
                            .whyItMatters(node.getWhyItMatters())
                            .status(statusByNodeId.getOrDefault(node.getNodeId(), FRONTEND_LOCKED_STATUS))
                            .completedAt(completedAt)
                            .fptCoverage(fptCoverage)
                            .fptResources(fptResources)
                            .skillName(node.getSkill() != null ? node.getSkill().getSkillName() : null)
                            .skillCategory(node.getSkill() != null ? node.getSkill().getCategory() : null)
                            .marketDemand(node.getSkill() != null
                                    ? demand.get(node.getSkill().getSkillId())
                                    : null)
                            // The score this node was already ordered by, now readable.
                            .priorityScore(priority == null ? null : priority.score())
                            .priorityLabel(priority == null ? null : priority.label().name())
                            .priorityReason(priority == null ? null : priority.reason())
                            .build();
                })
                .toList();
    }

    private static boolean evidenceSupportsNode(StudentSkillEvidence evidence, SkillNode node) {
        if (evidence.getNodeId() != null && evidence.getNodeId().equals(node.getNodeId())) return true;
        if (evidence.getSkillName() == null) return false;
        String name = evidence.getSkillName().trim().toLowerCase(java.util.Locale.ROOT);
        if (node.getSkill() != null && node.getSkill().getSkillName() != null
                && node.getSkill().getSkillName().trim().toLowerCase(java.util.Locale.ROOT).equals(name)) return true;
        if (node.getNodeName() != null
                && node.getNodeName().trim().toLowerCase(java.util.Locale.ROOT).equals(name)) return true;
        return node.getEvidenceKeywords() != null && node.getEvidenceKeywords().isArray()
                && java.util.stream.StreamSupport.stream(node.getEvidenceKeywords().spliterator(), false)
                .map(value -> value.asText("").trim().toLowerCase(java.util.Locale.ROOT))
                .anyMatch(name::equals);
    }

    private static double effectiveEvidenceThreshold(SkillNode node, StudentRoadmapContext context) {
        double nodeBar = node.getRequiredProficiency() != null && node.getRequiredProficiency() > 0
                ? node.getRequiredProficiency() / 100.0 : 0.70;
        ImportanceLevel importance = node.getSkill() == null || context == null
                ? null : context.importanceBySkillId().get(node.getSkill().getSkillId());
        double importanceBar = importance == ImportanceLevel.HIGH ? 0.85
                : importance == ImportanceLevel.LOW ? 0.60 : 0.70;
        return Math.max(nodeBar, importanceBar);
    }

    private static String evidenceDecision(SkillNode node, boolean topic, int total, int done,
                                           List<StudentSkillEvidence> evidence, double threshold,
                                           String status) {
        if (topic) {
            int needed = total == 0 ? 0 : (int) Math.ceil(total * 0.6);
            return "Topic progress: " + done + "/" + total + " direct sub-skills complete"
                    + (total > 0 ? "; needs at least " + needed + "." : ".");
        }
        if (FRONTEND_COMPLETED_STATUS.equals(status)) return "Completed; the accepted evidence met this node's rule.";
        if (!"EVIDENCE_ALLOWED".equalsIgnoreCase(node.getCompletionPolicy())) {
            return "This node is completed through its learning rule, not automatically from imported evidence.";
        }
        if (evidence.isEmpty()) return "No accepted evidence directly matches this node yet.";
        double best = evidence.stream().filter(e -> e.getConfidence() != null)
                .mapToDouble(e -> e.getConfidence().doubleValue()).max().orElse(0);
        if (best < threshold) {
            return String.format(java.util.Locale.ROOT,
                    "Best matching evidence is %.0f%%; this node requires %.0f%%.", best * 100, threshold * 100);
        }
        return "Evidence meets the confidence bar; complete prerequisites to unlock this node.";
    }

    // Session topics that are class logistics, not learning content — never worth
    // surfacing on a skill node.
    private static final List<String> SESSION_TOPIC_SKIP = List.of(
            "orientation", "lab room", "regulation", "review", "revision", "revison",
            "exam", "final", "project evaluation", "progress test", "mock", "guideline",
            "holiday", "quiz", "assignment submission");

    private static final int MAX_SESSIONS_PER_SUBJECT = 6;
    private static final int MAX_NODE_RESOURCES = 24;

    /**
     * Pick the resources actually worth showing on one skill node. A subject's syllabus
     * has ~60 session rows, most of them just a weekly schedule entry with nothing to
     * open; dumping them all is noise. So: keep every material (textbook / online course
     * link), but for sessions keep only ones that link to something, drop pure logistics
     * (orientation / exam / project evaluation …), and collapse repeated chapters — then
     * cap so a node with several covering subjects stays readable. Materials come first.
     */
    private List<FptNodeResourceResponse> buildNodeResources(
            List<FptSubject> subjects, Map<String, List<FptSubjectResource>> resourcesByCode) {
        List<FptNodeResourceResponse> materials = new ArrayList<>();
        List<FptNodeResourceResponse> sessions = new ArrayList<>();
        Set<String> seenMaterialTitles = new HashSet<>();
        Set<String> seenSessionTopics = new HashSet<>();

        for (FptSubject subject : subjects) {
            int keptSessions = 0;
            for (FptSubjectResource r : resourcesByCode.getOrDefault(subject.getCode(), List.of())) {
                boolean isSession = "SESSION".equals(r.getKind().name());
                String url = r.getUrl() != null ? r.getUrl().trim() : "";

                if (!isSession) {
                    String key = (r.getTitle() == null ? "" : r.getTitle().trim().toLowerCase());
                    if (!key.isEmpty() && !seenMaterialTitles.add(key)) continue;
                    materials.add(toResource(r));
                    continue;
                }

                // Sessions: only ones you can actually open, minus logistics, deduped by topic.
                if (url.isEmpty()) continue;
                if (keptSessions >= MAX_SESSIONS_PER_SUBJECT) continue;
                String topic = (r.getTopic() == null ? "" : r.getTopic().trim().toLowerCase());
                if (topic.isEmpty()) continue;
                if (SESSION_TOPIC_SKIP.stream().anyMatch(topic::contains)) continue;
                String norm = subject.getCode() + "|" + topic.replace("(cnt)", "").replace("(cont)", "").trim();
                if (!seenSessionTopics.add(norm)) continue;
                sessions.add(toResource(r));
                keptSessions++;
            }
        }

        List<FptNodeResourceResponse> out = new ArrayList<>(materials);
        out.addAll(sessions);
        return out.size() > MAX_NODE_RESOURCES ? out.subList(0, MAX_NODE_RESOURCES) : out;
    }

    private static FptNodeResourceResponse toResource(FptSubjectResource r) {
        return FptNodeResourceResponse.builder()
                .subjectCode(r.getSubjectCode())
                .kind(r.getKind().name())
                .title(r.getTitle())
                .url(r.getUrl())
                .topic(r.getTopic())
                .build();
    }
}
