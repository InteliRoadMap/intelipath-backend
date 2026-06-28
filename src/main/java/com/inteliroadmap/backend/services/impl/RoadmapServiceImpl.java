package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.request.UpdateUserProgressRequest;
import com.inteliroadmap.backend.domain.dto.request.UpdateNodeProgressRequest;
import com.inteliroadmap.backend.domain.dto.response.StudentRoadmapNodeResponse;
import com.inteliroadmap.backend.domain.dto.response.StudentRoadmapResourceResponse;
import com.inteliroadmap.backend.domain.dto.response.StudentRoadmapResponse;
import com.inteliroadmap.backend.domain.dto.response.RoadmapResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapNodeDto;
import com.inteliroadmap.backend.domain.dto.response.roadmap.SkillGapResponse;
import com.inteliroadmap.backend.domain.entity.*;
import com.inteliroadmap.backend.domain.enums.RoadmapStepStatus;
import com.inteliroadmap.backend.repositories.*;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.security.JwtService;
import com.inteliroadmap.backend.services.RoadmapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
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
        log.info("Roadmap Module: Fetching roadmap for career ID: {}", careerId);

        Student student = getAuthenticatedStudent();
        Optional<CareerRole> careerRoleOptional = careerRoleRepository.findById(careerId);
        if (careerRoleOptional.isEmpty()) {
            throw new ResourceNotFoundException("Career role not found");
        }

        CareerRole careerRole = careerRoleOptional.get();

        List<SkillNode> nodes = skillNodeRepository.findByCareerId(careerId);
        List<StudentProgress> progresses = studentProgressRepository.findByStudentId(student.getUserId());

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
        log.info("Roadmap Module: Fetching authenticated student roadmap");

        // Fetch authenticated student
        Student student = getAuthenticatedStudent();
        // If the student hasn't selected a career, return an empty roadmap
        if (student.getCareerId() == null) {
            return StudentRoadmapResponse.builder()
                    .progress(0)
                    .nodes(List.of())
                    .build();
        }

        // Look up the career role and associated skill nodes
        CareerRole careerRole = careerRoleRepository.findByCareerId(student.getCareerId());
        List<SkillNode> nodes = skillNodeRepository
                .findByCareerIdOrderByNodeLevelAscNodeNameAsc(careerRole.getCareerId());

        if (nodes.isEmpty()) {
            return StudentRoadmapResponse.builder()
                    .targetCareerRole(careerRole.getCareerName())
                    .progress(0)
                    .nodes(List.of())
                    .build();
        }

        // Fetch student's progress for these specific nodes and map by node ID
        Map<UUID, StudentProgress> progressByNodeId = mapProgressByNodeId(
                studentProgressRepository.findByStudentIdAndNodeIdIn(
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
                .nodes(buildRoadmapTree(nodes, statusByNodeId))
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
        log.info("Roadmap Module: Calculating total progress for career ID: {}", careerId);

        Student student = getAuthenticatedStudent();

        List<SkillNode> allNodes = skillNodeRepository.findByCareerId(careerId);
        if (allNodes.isEmpty()) {
            return RoadmapResponse.builder().totalProgress(0.0).build();
        }

        List<StudentProgress> progresses = studentProgressRepository.findByStudentId(student.getUserId());
        Set<UUID> careerNodeIds = allNodes.stream().map(SkillNode::getNodeId).collect(Collectors.toSet());

        long completedCount = progresses.stream()
                .filter(p -> RoadmapStepStatus.COMPLETED == p.getStatus())
                .filter(p -> p.getNodeId() != null && careerNodeIds.contains(p.getNodeId()))
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
        log.info("Roadmap Module: Fetching Node information");

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
        log.info("Roadmap Module: Updating Node progress");

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
                studentProgressRepository.findByStudentIdAndNodeId(student.getUserId(), node.getNodeId());
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
    public com.inteliroadmap.backend.domain.dto.response.roadmap.StudentRoadmapResponse
    getStudentRoadmap(String authHeader) {
        Student student = getStudentFromAuthHeader(authHeader);
        CareerRole careerRole = careerRoleRepository.findByCareerId(student.getCareerId());
        if (careerRole == null) {
            throw new ResourceNotFoundException("Student has not selected a career path yet.");
        }

        List<SkillNode> nodes = skillNodeRepository
                .findByCareerIdOrderByNodeLevelAscNodeNameAsc(careerRole.getCareerId());
        List<StudentProgress> progressList = studentProgressRepository.findByStudentId(student.getUserId());
        Map<UUID, String> progressMap = progressList.stream()
                .collect(Collectors.toMap(
                        StudentProgress::getNodeId,
                        progress -> progress.getStatus().toFrontendValue()
                ));

        List<RoadmapNodeDto> nodeDtos = nodes.stream()
                .map(node -> mapToLegacyNodeDto(node, progressMap.get(node.getNodeId())))
                .toList();

        int completed = (int) progressList.stream()
                .filter(progress -> RoadmapStepStatus.COMPLETED == progress.getStatus())
                .count();
        int progressPercent = nodes.isEmpty() ? 0 : (completed * 100) / nodes.size();

        return com.inteliroadmap.backend.domain.dto.response.roadmap.StudentRoadmapResponse.builder()
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
    public com.inteliroadmap.backend.domain.dto.response.roadmap.StudentRoadmapResponse
    getCareerRoadmapTemplate(UUID careerId) {
        Optional<CareerRole> careerRoleOptional = careerRoleRepository.findById(careerId);
        if (careerRoleOptional.isEmpty()) {
            throw new ResourceNotFoundException("Career not found");
        }

        CareerRole careerRole = careerRoleOptional.get();
        List<SkillNode> nodes = skillNodeRepository
                .findByCareerIdOrderByNodeLevelAscNodeNameAsc(careerId);
        List<RoadmapNodeDto> nodeDtos = nodes.stream()
                .map(node -> mapToLegacyNodeDto(node, "not_started"))
                .toList();

        return com.inteliroadmap.backend.domain.dto.response.roadmap.StudentRoadmapResponse.builder()
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
        List<SkillNode> nodes = skillNodeRepository.findByCareerId(careerId);
        if (nodes.isEmpty()) {
            return 0;
        }

        List<UUID> nodeIds = nodes.stream()
                .map(SkillNode::getNodeId)
                .toList();
        List<StudentProgress> progressList =
                studentProgressRepository.findByStudentIdAndNodeIdIn(
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
                studentProgressRepository.findByStudentIdAndNodeId(student.getUserId(), node.getNodeId());

        StudentProgress progress;
        if (progressOptional.isPresent()) {
            progress = progressOptional.get();
        } else {
            progress = StudentProgress.builder()
                    .studentId(student.getUserId())
                    .nodeId(node.getNodeId())
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
            if (node.getSkillId() != null) {
                Skill skill = skillRepository.findById(node.getSkillId()).orElse(null);
                if (skill != null) {
                    boolean hasSkill = studentSkillRepository.existsByStudentIdAndSkillId(student.getUserId(), skill.getSkillId());
                    if (!hasSkill) {
                        List<SkillNode> allNodesForSkill = skillNodeRepository
                                .findBySkillIdAndCareerId(
                                        skill.getSkillId(),
                                        student.getCareerId()
                                );
                        boolean allCompleted = true;
                        
                        for (SkillNode skillNode : allNodesForSkill) {
                            if (skillNode.getNodeId().equals(node.getNodeId())) {
                                continue;
                            }
                            StudentProgress nodeProgress = studentProgressRepository.findByStudentIdAndNodeId(student.getUserId(), skillNode.getNodeId()).orElse(null);
                            if (nodeProgress == null || nodeProgress.getStatus() != RoadmapStepStatus.COMPLETED) {
                                allCompleted = false;
                                break;
                            }
                        }

                        if (allCompleted) {
                            StudentSkill newSkill = StudentSkill.builder()
                                    .studentId(student.getUserId())
                                    .skillId(skill.getSkillId())
                                    .build();
                            studentSkillRepository.save(newSkill);
                            log.info("Auto-synced skill {} to student {} after all required nodes were completed", skill.getSkillName(), student.getUserId());
                        }
                    }
                }
            }
        }
        studentProgressRepository.save(progress);
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
        CareerRole careerRole = careerRoleRepository.findByCareerId(student.getCareerId());
        if (careerRole == null) {
            throw new ResourceNotFoundException("Student has not selected a career path yet.");
        }

        List<StudentSkill> currentStudentSkills = studentSkillRepository.findByStudentId(student.getUserId());
        List<UUID> currentSkillIds = currentStudentSkills.stream()
                .map(StudentSkill::getSkillId)
                .collect(Collectors.toList());
        List<Skill> currentSkills = skillRepository.findAllById(currentSkillIds);
        Set<String> currentSkillNames = currentSkills.stream()
                .map(Skill::getSkillName)
                .collect(Collectors.toSet());

        List<CareerRequiredSkill> requiredSkills =
                careerRequiredSkillRepository.findByCareerRoleId(careerRole.getCareerId());
        List<UUID> reqSkillIds = requiredSkills.stream()
                .map(CareerRequiredSkill::getSkillId)
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
        String childNodeOf = node.getParentNode() != null ?
                skillNodeRepository.findByNodeId(node.getParentNode()).getNodeName() : null;

        return RoadmapNodeDto.builder()
                .nodeId(node.getNodeId())
                .nodeName(node.getNodeName())
                .prerequisite(node.getPrerequisite())
                .parentNode(childNodeOf)
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
            if (progress.getNodeId() != null) {
                progressByNodeId.put(progress.getNodeId(), progress);
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
    private Map<UUID, String> buildFrontendStatusMap(
            List<SkillNode> nodes,
            Map<UUID, StudentProgress> progressByNodeId
    ) {
        Map<UUID, String> statusByNodeId = new HashMap<>();

        for (SkillNode node : nodes) {
            StudentProgress progress = progressByNodeId.get(node.getNodeId());
            if (progress != null) {
                if (RoadmapStepStatus.COMPLETED == progress.getStatus()) {
                    statusByNodeId.put(node.getNodeId(), FRONTEND_COMPLETED_STATUS);
                    continue;
                }
                if (RoadmapStepStatus.IN_PROGRESS == progress.getStatus()) {
                    statusByNodeId.put(node.getNodeId(), FRONTEND_IN_PROGRESS_STATUS);
                    continue;
                }
            }

            if (node.getParentNode() == null) {
                statusByNodeId.put(node.getNodeId(), FRONTEND_CURRENT_STATUS);
            } else {
                String parentStatus = statusByNodeId.get(node.getParentNode());
                if (FRONTEND_COMPLETED_STATUS.equals(parentStatus)) {
                    statusByNodeId.put(node.getNodeId(), FRONTEND_CURRENT_STATUS);
                } else {
                    statusByNodeId.put(node.getNodeId(), FRONTEND_LOCKED_STATUS);
                }
            }
        }

        return statusByNodeId;
    }

    /**
     * Calculates the overall progress percentage for a roadmap.
     *
     * @param nodes the list of {@link SkillNode}s in the roadmap
     * @param progressByNodeId the mapped student progress by node ID
     * @return the calculated progress percentage (0 to 100)
     */
    private int calculateProgress(List<SkillNode> nodes, Map<UUID, StudentProgress> progressByNodeId) {
        if (nodes.isEmpty()) {
            return 0;
        }

        long completedCount = nodes.stream()
                .map(SkillNode::getNodeId)
                .map(progressByNodeId::get)
                .filter(Objects::nonNull)
                .filter(progress -> RoadmapStepStatus.COMPLETED == progress.getStatus())
                .count();

        return (int) Math.round(((double) completedCount / nodes.size()) * 100);
    }

    /**
     * Builds a hierarchical tree of roadmap nodes from a flat list of nodes.
     *
     * @param nodes the list of {@link SkillNode}s
     * @param statusByNodeId the mapped frontend status by node ID
     * @return a list of root {@link StudentRoadmapNodeResponse}s, each containing its children
     */
    private List<StudentRoadmapNodeResponse> buildRoadmapTree(
            List<SkillNode> nodes,
            Map<UUID, String> statusByNodeId
    ) {
        Map<UUID, List<SkillNode>> childrenByParentId = new HashMap<>();
        List<SkillNode> rootNodes = new ArrayList<>();

        for (SkillNode node : nodes) {
            if (node.getParentNode() == null) {
                rootNodes.add(node);
                continue;
            }

            UUID parentId = node.getParentNode();
            childrenByParentId.computeIfAbsent(parentId, key -> new ArrayList<>()).add(node);
        }

        return rootNodes.stream()
                .map(node -> toRoadmapNodeResponse(node, statusByNodeId, childrenByParentId))
                .toList();
    }

    /**
     * Recursively converts a {@link SkillNode} into a {@link StudentRoadmapNodeResponse} tree node.
     *
     * @param node the current skill node
     * @param statusByNodeId the mapped frontend status by node ID
     * @param childrenByParentId the map of parent IDs to their children nodes
     * @return the constructed {@link StudentRoadmapNodeResponse}
     */
    private StudentRoadmapNodeResponse toRoadmapNodeResponse(
            SkillNode node,
            Map<UUID, String> statusByNodeId,
            Map<UUID, List<SkillNode>> childrenByParentId
    ) {
        List<StudentRoadmapNodeResponse> children = childrenByParentId
                .getOrDefault(node.getNodeId(), List.of())
                .stream()
                .map(child -> toRoadmapNodeResponse(child, statusByNodeId, childrenByParentId))
                .toList();

        return StudentRoadmapNodeResponse.builder()
                .id(node.getNodeId())
                .title(node.getNodeName())
                .status(statusByNodeId.getOrDefault(node.getNodeId(), FRONTEND_LOCKED_STATUS))
                .description(node.getDescription())
                .nodeLevel(node.getNodeLevel())
                .parentNode(node.getParentNode() != null ? node.getParentNode() : null)
                .resources(toResourceResponses(node.getResource()))
                .children(children)
                .build();
    }

    /**
     * Converts a raw resource object into a list of {@link StudentRoadmapResourceResponse}s.
     *
     * @param resource the raw resource object (map or list)
     * @return a list of parsed resource responses
     */
    private List<StudentRoadmapResourceResponse> toResourceResponses(Object resource) {
        if (resource instanceof Map<?, ?> resourceMap) {
            Object links = resourceMap.get("links");
            if (links instanceof List<?> linkList) {
                return linkList.stream()
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .filter(url -> !url.isBlank())
                        .map(this::toResourceResponse)
                        .toList();
            }
        }

        if (resource instanceof List<?> resourceList) {
            return resourceList.stream()
                    .map(obj -> {
                        if (obj instanceof Map<?, ?> map) {
                            return toResourceResponse(map);
                        } else if (obj instanceof String url) {
                            return toResourceResponse(url);
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .toList();
        }

        return List.of();
    }

    /**
     * Converts a URL string into a {@link StudentRoadmapResourceResponse}.
     *
     * @param url the resource URL
     * @return the constructed resource response
     */
    private StudentRoadmapResourceResponse toResourceResponse(String url) {
        return StudentRoadmapResourceResponse.builder()
                .title(url)
                .url(url)
                .type("article")
                .build();
    }

    /**
     * Converts a resource map into a {@link StudentRoadmapResourceResponse}.
     * Extracts the URL and title from the map attributes.
     *
     * @param resourceMap the map containing resource attributes
     * @return the constructed resource response
     */
    private StudentRoadmapResourceResponse toResourceResponse(Map<?, ?> resourceMap) {
        Object url = firstNonNull(resourceMap.get("url"), resourceMap.get("ref1"));
        if (url == null || url.toString().isBlank()) {
            return StudentRoadmapResourceResponse.builder().build();
        }

        Object title = firstNonNull(resourceMap.get("title"), url);
        return StudentRoadmapResourceResponse.builder()
                .title(title.toString())
                .url(url.toString())
                .type("article")
                .build();
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
