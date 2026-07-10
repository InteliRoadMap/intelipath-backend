package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.components.RoadmapProgressCalculator;
import com.inteliroadmap.backend.domain.dto.request.UpdateUserProgressRequest;
import com.inteliroadmap.backend.domain.dto.request.UpdateNodeProgressRequest;
import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapNodeDto;
import com.inteliroadmap.backend.domain.dto.response.roadmap.SkillGapResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.StudentRoadmapResponse;
import com.inteliroadmap.backend.domain.entity.CareerRequiredSkill;
import com.inteliroadmap.backend.domain.entity.CareerRole;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.entity.RoadmapNodeLayout;
import com.inteliroadmap.backend.domain.entity.SkillNode;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.StudentProgress;
import com.inteliroadmap.backend.domain.entity.StudentSkill;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.domain.enums.RoadmapStepStatus;
import com.inteliroadmap.backend.repositories.CareerRequiredSkillRepository;
import com.inteliroadmap.backend.repositories.CareerRoleRepository;
import com.inteliroadmap.backend.repositories.RoadmapNodeLayoutRepository;
import com.inteliroadmap.backend.repositories.SkillNodeRepository;
import com.inteliroadmap.backend.repositories.SkillRepository;
import com.inteliroadmap.backend.repositories.StudentProgressRepository;
import com.inteliroadmap.backend.repositories.StudentRepository;
import com.inteliroadmap.backend.repositories.StudentSkillRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.security.JwtService;
import com.inteliroadmap.backend.services.RoadmapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final CareerRequiredSkillRepository careerRequiredSkillRepository;
    private final SkillRepository skillRepository;
    private final JwtService jwtService;

    private static final String COMPLETED_STATUS = "COMPLETED";
    private static final String FRONTEND_COMPLETED_STATUS = "completed";
    private static final String FRONTEND_IN_PROGRESS_STATUS = "in_progress";
    private static final String FRONTEND_CURRENT_STATUS = "current";
    private static final String FRONTEND_LOCKED_STATUS = "locked";
    private final com.inteliroadmap.backend.services.AuthenticatedStudentService AuthenticatedStudentService;

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
        return AuthenticatedStudentService.getOrCreateStudent();
    }

    /**
     * Retrieves the full learning roadmap for a specific career role.
     * Includes all required skill nodes and the authenticated student's progress for those nodes.
     *
     * @param careerId The UUID of the target career role
     * @return RoadmapResponse containing the skill nodes and the student's progress
     */
    @Transactional(readOnly = true)
    @Override
    public RoadmapResponse getRoadmap(UUID careerId) {
        log.info("RoadmapServiceImpl: Fetching roadmap for career ID: {}", careerId);

        Student student = getAuthenticatedStudent();
        Optional<CareerRole> careerRoleOptional = careerRoleRepository.findById(careerId);
        if (careerRoleOptional.isEmpty()) {
            throw new ResourceNotFoundException("Career role not found");
        }

        CareerRole careerRole = careerRoleOptional.get();

        List<SkillNode> nodes = skillNodeRepository.findByCareerRole_CareerId(careerId);
        List<StudentProgress> progresses = studentProgressRepository.findByStudent_UserId(student.getUserId());

        return RoadmapResponse.builder()
                .skillNodes(nodes)
                .progresses(progresses)
                .build();
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
        log.info("RoadmapServiceImpl: Fetching authenticated student roadmap");

        // Fetch authenticated student
        Student student = getAuthenticatedStudent();
        // If the student hasn't selected a career, return an empty roadmap
        if (student.getCareerRole().getCareerId() == null) {
            return StudentRoadmapResponse.builder()
                    .progress(0)
                    .nodes(List.of())
                    .build();
        }

        // Look up the career role and associated skill nodes
        CareerRole careerRole = careerRoleRepository.findByCareerId(student.getCareerRole().getCareerId());
        List<SkillNode> nodes = skillNodeRepository
                .findByCareerRole_CareerIdOrderByNodeLevelAscNodeNameAsc(careerRole.getCareerId());

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

        // Compute frontend statuses (e.g., locked, current, completed) and overall progress
        Map<UUID, String> statusByNodeId = buildFrontendStatusMap(nodes, progressByNodeId);
        int progress = calculateProgress(nodes, progressByNodeId);

        // Build the hierarchical roadmap tree
        return StudentRoadmapResponse.builder()
                .targetCareerRole(careerRole.getCareerName())
                .progress(progress)
                .nodes(buildRoadmapTree(nodes, statusByNodeId, progressByNodeId))
                .build();
    }

    /**
     * Calculates the authenticated student's overall completion progress for a specific career roadmap.
     * Computes the percentage of completed skill nodes out of the total required nodes for the career.
     *
     * @param careerId The UUID of the target career role
     * @return RoadmapResponse containing the calculated totalProgress percentage (0.0 to 100.0)
     */
    @Transactional(readOnly = true)
    @Override
    public RoadmapResponse getRoadmapProgress(UUID careerId) {
        log.info("RoadmapServiceImpl: Calculating total progress for career ID: {}", careerId);

        Student student = getAuthenticatedStudent();

        List<SkillNode> allNodes = skillNodeRepository.findByCareerRole_CareerId(careerId);
        if (allNodes.isEmpty()) {
            return RoadmapResponse.builder().totalProgress(0.0).build();
        }

        List<StudentProgress> progresses = studentProgressRepository.findByStudent_UserId(student.getUserId());
        Set<UUID> careerNodeIds = allNodes.stream().map(SkillNode::getNodeId).collect(Collectors.toSet());

        long completedCount = progresses.stream()
                .filter(p -> RoadmapStepStatus.COMPLETED == p.getStatus())
                .filter(p -> p.getSkillNode().getNodeId() != null && careerNodeIds.contains(p.getSkillNode().getNodeId()))
                .count();

        double percentage = ((double) completedCount / allNodes.size()) * 100.0;

        return RoadmapResponse.builder()
                .totalProgress(percentage)
                .build();
    }

    /**
     * Fetches detailed information about a single specific skill node.
     *
     * @param nodeId The UUID of the skill node to fetch
     * @return RoadmapResponse containing the detailed skill node information
     */
    @Override
    public RoadmapResponse getNode(UUID nodeId) {
        log.info("RoadmapServiceImpl: Fetching Node information");

        Optional<SkillNode> nodeOptional = skillNodeRepository.findById(nodeId);
        if (nodeOptional.isEmpty()) {
            throw new ResourceNotFoundException("Node not found");
        }

        SkillNode node = nodeOptional.get();

        return RoadmapResponse.builder()
                .skillNode(node)
                .build();
    }

    /**
     * Updates the authenticated student's progress for a specific skill node, marking it as COMPLETED.
     * Securely identifies the student via JWT, ignoring any client-provided student IDs.
     *
     * @param request The payload containing the target node ID
     * @return RoadmapResponse confirming the successful status update
     */
    @Transactional
    @Override
    public RoadmapResponse updateNodeProgress(UpdateUserProgressRequest request) {
        log.info("RoadmapServiceImpl: Updating Node progress");

        // Identify the current student
        Student student = getAuthenticatedStudent();

        // Verify the requested node exists
        Optional<SkillNode> nodeOptional = skillNodeRepository.findById(request.getNodeId());
        if (nodeOptional.isEmpty()) {
            throw new ResourceNotFoundException("Node not found");
        }

        SkillNode node = nodeOptional.get();

        // Check if the student has progress recorded for this node
        Optional<StudentProgress> progressOptional =
                studentProgressRepository.findByStudent_UserIdAndSkillNode_NodeId(student.getUserId(), node.getNodeId());
        if (progressOptional.isEmpty()) {
            throw new ResourceNotFoundException("Student progress not found for this node");
        }

        StudentProgress progress = progressOptional.get();

        // If the node is currently in progress, mark it completed and record the time
        if(RoadmapStepStatus.IN_PROGRESS == progress.getStatus()){
            progress.setStatus(RoadmapStepStatus.COMPLETED);
            progress.setCompletedAt(LocalDateTime.now());
            studentProgressRepository.save(progress);
        }
        
        return RoadmapResponse.builder()
                .updateStatus("Update Node status successfully")
                .build();
    }

    /**
     * Retrieves the authenticated student's roadmap using the existing roadmap API contract.
     *
     * @param authHeader authorization header containing the Bearer access token
     * @return flat roadmap response used by the existing roadmap controller
     */
    @Transactional(readOnly = true)
    @Override
    public StudentRoadmapResponse getStudentRoadmap(String authHeader) {
        Student student = getStudentFromAuthHeader(authHeader);
        if (student.getCareerRole() == null) {
            throw new ResourceNotFoundException("Student has not selected a career path yet.");
        }
        CareerRole careerRole = careerRoleRepository.findByCareerId(student.getCareerRole().getCareerId());
        if (careerRole == null) {
            throw new ResourceNotFoundException("Student has not selected a career path yet.");
        }

        List<SkillNode> nodes = skillNodeRepository
                .findByCareerRole_CareerIdOrderByNodeLevelAscNodeNameAsc(careerRole.getCareerId());
        List<StudentProgress> progressList = studentProgressRepository.findByStudent_UserId(student.getUserId());
        Map<UUID, String> progressMap = progressList.stream()
                .collect(Collectors.toMap(
                        s -> s.getSkillNode().getNodeId(),
                        progress -> progress.getStatus() != null ? progress.getStatus().toFrontendValue() : "not_started",
                        (existing, replacement) -> existing
                ));

        List<RoadmapNodeDto> nodeDtos = nodes.stream()
                .map(node -> mapToLegacyNodeDto(node, progressMap.get(node.getNodeId())))
                .toList();

        int completed = (int) progressList.stream()
                .filter(progress -> RoadmapStepStatus.COMPLETED == progress.getStatus())
                .count();
        int progressPercent = nodes.isEmpty() ? 0 : (completed * 100) / nodes.size();

        return StudentRoadmapResponse.builder()
                .targetCareerRole(careerRole.getCareerName())
                .progress(progressPercent)
                .nodes(nodeDtos)
                .build();
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
                .findByCareerRole_CareerIdOrderByNodeLevelAscNodeNameAsc(careerId);
        List<RoadmapNodeDto> nodeDtos = nodes.stream()
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
     * @param authHeader authorization header containing the Bearer access token
     * @return completion percentage from 0 to 100
     */
    @Transactional(readOnly = true)
    @Override
    public Integer getCareerProgress(UUID careerId, String authHeader) {
        Student student = getStudentFromAuthHeader(authHeader);
        List<SkillNode> nodes = skillNodeRepository.findByCareerRole_CareerId(careerId);
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

        int completed = (int) progressList.stream()
                .filter(progress -> RoadmapStepStatus.COMPLETED == progress.getStatus())
                .count();
        return (completed * 100) / nodes.size();
    }

    /**
     * Retrieves one roadmap node using the existing roadmap API response.
     *
     * @param nodeId roadmap node UUID
     * @return roadmap node detail response
     */
    @Transactional(readOnly = true)
    @Override
    public RoadmapNodeDto getNodeDetail(UUID nodeId) {
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
     * @param authHeader authorization header containing the Bearer access token
     */
    @Transactional
    @Override
    public void updateNodeProgress(UpdateNodeProgressRequest request, String authHeader) {
        Student student = getStudentFromAuthHeader(authHeader);

        Optional<SkillNode> nodeOptional = skillNodeRepository.findById(request.getNodeId());
        if (nodeOptional.isEmpty()) {
            throw new ResourceNotFoundException("Node not found");
        }

        SkillNode node = nodeOptional.get();
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
        
        progress.setStatus(newStatus);
        if (RoadmapStepStatus.COMPLETED == newStatus
                && progress.getCompletedAt() == null) {
            progress.setCompletedAt(java.time.LocalDateTime.now());
            
            // Auto-sync skill to profile
            if (node.getSkill() != null) {
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
        syncTopicAutoCompletion(student, node);
    }

    /**
     * Walks up the parent chain of {@code changedNode} and keeps each topic node's
     * stored progress in sync with the auto-completion rule: a topic is COMPLETED once
     * its child weight crosses {@link #parentCompletionThreshold}, and reverted back to
     * IN_PROGRESS if a child is later un-completed and drags it below the threshold.
     */
    private void syncTopicAutoCompletion(Student student, SkillNode changedNode) {
        SkillNode topic = changedNode.getParentNode();
        while (topic != null) {
            final SkillNode currentTopic = topic;
            List<SkillNode> children = skillNodeRepository.findByParentNode_NodeId(currentTopic.getNodeId());
            long totalWeight = 0;
            long doneWeight = 0;
            for (SkillNode child : children) {
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
     * Compares selected student skills with the target career requirements.
     *
     * @param authHeader authorization header containing the Bearer access token
     * @return current and missing skill names
     */
    @Transactional(readOnly = true)
    @Override
    public SkillGapResponse compareSkills(String authHeader) {
        Student student = getStudentFromAuthHeader(authHeader);
        CareerRole careerRole = careerRoleRepository.findByCareerId(student.getCareerRole().getCareerId());
        if (careerRole == null) {
            throw new ResourceNotFoundException("Student has not selected a career path yet.");
        }

        List<StudentSkill> currentStudentSkills = studentSkillRepository.findByStudent_UserId(student.getUserId());
        List<UUID> currentSkillIds = currentStudentSkills.stream()
                .map(s -> s.getSkill().getSkillId())
                .collect(Collectors.toList());
        List<Skill> currentSkills = skillRepository.findAllById(currentSkillIds);
        Set<String> currentSkillNames = currentSkills.stream()
                .map(Skill::getSkillName)
                .collect(Collectors.toSet());

        List<CareerRequiredSkill> requiredSkills =
                careerRequiredSkillRepository.findByCareerRole_CareerId(careerRole.getCareerId());
        List<UUID> reqSkillIds = requiredSkills.stream()
                .map(s -> s.getSkill().getSkillId())
                .collect(Collectors.toList());
        List<Skill> reqSkills = skillRepository.findAllById(reqSkillIds);
        
        List<String> missingSkills = reqSkills.stream()
                .map(Skill::getSkillName)
                .filter(skillName -> !currentSkillNames.contains(skillName))
                .toList();

        return SkillGapResponse.builder()
                .currentSkills(new ArrayList<>(currentSkillNames))
                .missingSkills(missingSkills)
                .build();
    }

    /**
     * Retrieves the student entity from the provided authorization header.
     *
     * @param authHeader the authorization header containing the Bearer token
     * @return the {@link Student} associated with the token
     * @throws RuntimeException if the authorization header is invalid
     * @throws ResourceNotFoundException if the user or student profile cannot be found
     */
    private Student getStudentFromAuthHeader(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Invalid Authorization header");
        }

        String token = authHeader.substring(7);
        String email = jwtService.extractEmail(token);
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
     * Maps a {@link SkillNode} to a legacy {@link RoadmapNodeDto}.
     *
     * @param node the skill node to map
     * @param status the progress status of the node, defaults to "not_started" if null
     * @return the mapped {@link RoadmapNodeDto}
     */
    private RoadmapNodeDto mapToLegacyNodeDto(SkillNode node, String status) {
        String parentNode = node.getParentNode() != null ?
                node.getParentNode().getNodeName() : null;

        return RoadmapNodeDto.builder()
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
            Map<UUID, StudentProgress> progressByNodeId
    ) {
        Map<UUID, String> statusByNodeId = new HashMap<>();
        Set<String> passedStages = findPassedStages(nodes, progressByNodeId);
        Set<UUID> topicIds = topicParentIds(nodes);
        Map<UUID, List<SkillNode>> childrenByParent = childrenByParent(nodes);

        // Parents/previous nodes must be classified before their dependants,
        // regardless of the level/name order the nodes were fetched in.
        for (SkillNode node : orderDependenciesFirst(nodes)) {
            StudentProgress progress = progressByNodeId.get(node.getNodeId());
            if (progress != null && RoadmapStepStatus.COMPLETED == progress.getStatus()) {
                statusByNodeId.put(node.getNodeId(), FRONTEND_COMPLETED_STATUS);
                continue;
            }
            if (progress != null && RoadmapStepStatus.IN_PROGRESS == progress.getStatus()) {
                statusByNodeId.put(node.getNodeId(), FRONTEND_IN_PROGRESS_STATUS);
                continue;
            }

            if (topicIds.contains(node.getNodeId())) {
                // A topic (spine) node is gated only by its own sequential order
                // (previousNode) and stage — never by its own children. Once
                // reachable it auto-completes as soon as enough child weight is done,
                // otherwise it stays the current focus while its sub-skills are learned.
                boolean gateLocked = isStageLocked(node, passedStages)
                        || isSequentialGateLocked(node.getPreviousNode(), statusByNodeId);
                if (gateLocked) {
                    statusByNodeId.put(node.getNodeId(), FRONTEND_LOCKED_STATUS);
                } else if (childCompletionRatio(node, childrenByParent, progressByNodeId) >= parentCompletionThreshold) {
                    statusByNodeId.put(node.getNodeId(), FRONTEND_COMPLETED_STATUS);
                } else {
                    statusByNodeId.put(node.getNodeId(), FRONTEND_CURRENT_STATUS);
                }
                continue;
            }

            // Leaf / child node: unlocks as soon as its parent topic is REACHED
            // (i.e. no longer locked) rather than fully completed, plus any
            // sequential previousNode ordering among siblings/spine leaves.
            boolean locked = isStageLocked(node, passedStages)
                    || isParentReachedGateLocked(node.getParentNode(), statusByNodeId)
                    || isSequentialGateLocked(node.getPreviousNode(), statusByNodeId);
            statusByNodeId.put(node.getNodeId(), locked ? FRONTEND_LOCKED_STATUS : FRONTEND_CURRENT_STATUS);
        }

        return statusByNodeId;
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

    /** Completed-child weight over total-child weight for a topic (0.0 when it has no children). */
    private double childCompletionRatio(
            SkillNode topic,
            Map<UUID, List<SkillNode>> childrenByParent,
            Map<UUID, StudentProgress> progressByNodeId
    ) {
        List<SkillNode> children = childrenByParent.getOrDefault(topic.getNodeId(), List.of());
        long totalWeight = 0;
        long doneWeight = 0;
        for (SkillNode child : children) {
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

    /** Kahn-style ordering over parent/previous references; tolerates cycles by appending leftovers. */
    private List<SkillNode> orderDependenciesFirst(List<SkillNode> nodes) {
        Set<UUID> emitted = new HashSet<>();
        List<SkillNode> ordered = new ArrayList<>(nodes.size());
        List<SkillNode> remaining = new ArrayList<>(nodes);

        boolean progressMade = true;
        while (!remaining.isEmpty() && progressMade) {
            progressMade = false;
            Iterator<SkillNode> iterator = remaining.iterator();
            while (iterator.hasNext()) {
                SkillNode node = iterator.next();
                boolean parentReady = node.getParentNode() == null || emitted.contains(node.getParentNode().getNodeId());
                boolean previousReady = node.getPreviousNode() == null || emitted.contains(node.getPreviousNode().getNodeId());
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


    private List<RoadmapNodeDto> buildRoadmapTree(
            List<SkillNode> nodes,
            Map<UUID, String> statusByNodeId,
            Map<UUID, StudentProgress> progressByNodeId
    ) {
        // Presentation-only placement, joined in from the layout table.
        Map<UUID, RoadmapNodeLayout> layoutsByNodeId = new HashMap<>();
        for (RoadmapNodeLayout layout : roadmapNodeLayoutRepository
                .findByNodeIdIn(nodes.stream().map(SkillNode::getNodeId).toList())) {
            layoutsByNodeId.put(layout.getNodeId(), layout);
        }

        Set<UUID> topicIds = topicParentIds(nodes);
        Map<UUID, List<SkillNode>> childrenByParent = childrenByParent(nodes);

        return nodes.stream()
                .map(node -> {
                    RoadmapNodeLayout layout = layoutsByNodeId.get(node.getNodeId());
                    boolean isTopic = topicIds.contains(node.getNodeId());
                    List<SkillNode> children = isTopic
                            ? childrenByParent.getOrDefault(node.getNodeId(), List.of())
                            : List.of();
                    int childCompleted = (int) children.stream()
                            .map(c -> progressByNodeId.get(c.getNodeId()))
                            .filter(p -> p != null && RoadmapStepStatus.COMPLETED == p.getStatus())
                            .count();
                    return RoadmapNodeDto.builder()
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
                            .parentTopic(isTopic)
                            .childTotal(children.size())
                            .childCompleted(childCompleted)
                            .positionX(layout != null ? layout.getPositionX() : null)
                            .positionY(layout != null ? layout.getPositionY() : null)
                            .lane(layout != null ? layout.getLane() : null)
                            .displayOrder(layout != null ? layout.getDisplayOrder() : null)
                            .description(node.getDescription())
                            .resource(node.getResource())
                            .status(statusByNodeId.getOrDefault(node.getNodeId(), FRONTEND_LOCKED_STATUS))
                            .build();
                })
                .toList();
    }

    /**
     * Returns the first non-null object from the given arguments.
     *
     * @param first the first object to check
     * @param second the second object to check
     * @return the first non-null object, or null if both are null
     */
    private Object firstNonNull(Object first, Object second) {
        if (first != null) {
            return first;
        }
        return second;
    }
}
