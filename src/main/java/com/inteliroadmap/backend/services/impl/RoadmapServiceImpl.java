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

@Service
@RequiredArgsConstructor
@Slf4j
public class RoadmapServiceImpl {

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
    private final AuthenticatedStudentServiceImpl AuthenticatedStudentService;

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
    public RoadmapResponse getRoadmap(UUID careerId) {
        log.info("Roadmap Module: Fetching roadmap for career ID: {}", careerId);

        Student student = getAuthenticatedStudent();
        Optional<CareerRole> careerRoleOptional = careerRoleRepository.findById(careerId);
        if (careerRoleOptional.isEmpty()) {
            throw new ResourceNotFoundException("Career role not found");
        }

        CareerRole careerRole = careerRoleOptional.get();

        List<SkillNode> nodes = skillNodeRepository.findByCareerRole_CareerId(careerId);
        List<StudentProgress> progresses = studentProgressRepository.findByStudent(student);

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
    public StudentRoadmapResponse getStudentRoadmap() {
        log.info("Roadmap Module: Fetching authenticated student roadmap");

        Student student = getAuthenticatedStudent();
        if (student.getCareerRole() == null) {
            return StudentRoadmapResponse.builder()
                    .progress(0)
                    .nodes(List.of())
                    .build();
        }

        CareerRole careerRole = student.getCareerRole();
        List<SkillNode> nodes = skillNodeRepository
                .findByCareerRole_CareerIdOrderByLevelAscNodeNameAsc(careerRole.getCareerId());

        if (nodes.isEmpty()) {
            return StudentRoadmapResponse.builder()
                    .targetCareerRole(careerRole.getCareerName())
                    .progress(0)
                    .nodes(List.of())
                    .build();
        }

        Map<UUID, StudentProgress> progressByNodeId = mapProgressByNodeId(
                studentProgressRepository.findByStudent_UserIdAndSkillNode_NodeIdIn(
                        student.getUserId(),
                        nodes.stream().map(SkillNode::getNodeId).toList()
                )
        );

        Map<UUID, String> statusByNodeId = buildFrontendStatusMap(nodes, progressByNodeId);
        int progress = calculateProgress(nodes, progressByNodeId);

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
    public RoadmapResponse getRoadmapProgress(UUID careerId) {
        log.info("Roadmap Module: Calculating total progress for career ID: {}", careerId);

        Student student = getAuthenticatedStudent();

        List<SkillNode> allNodes = skillNodeRepository.findByCareerRole_CareerId(careerId);
        if (allNodes.isEmpty()) {
            return RoadmapResponse.builder().totalProgress(0.0).build();
        }

        List<StudentProgress> progresses = studentProgressRepository.findByStudent(student);

        long completedCount = progresses.stream()
                .filter(p -> RoadmapStepStatus.COMPLETED == p.getStatus())
                .filter(p -> p.getSkillNode() != null &&
                             p.getSkillNode().getCareerRole() != null &&
                             p.getSkillNode().getCareerRole().getCareerId().equals(careerId))
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
    public RoadmapResponse updateNodeProgress(UpdateUserProgressRequest request) {
        log.info("Roadmap Module: Updating Node progress");

        Student student = getAuthenticatedStudent();

        Optional<SkillNode> nodeOptional = skillNodeRepository.findById(request.getNodeId());
        if (nodeOptional.isEmpty()) {
            throw new ResourceNotFoundException("Node not found");
        }

        SkillNode node = nodeOptional.get();

        Optional<StudentProgress> progressOptional =
                studentProgressRepository.findByStudentAndSkillNode(student, node);
        if (progressOptional.isEmpty()) {
            throw new ResourceNotFoundException("Student progress not found for this node");
        }

        StudentProgress progress = progressOptional.get();

        if(RoadmapStepStatus.IN_PROGRESS == progress.getStatus()){
            progress.setStatus(RoadmapStepStatus.COMPLETED);
            progress.setCompleteAt(LocalDateTime.now());
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
    public com.inteliroadmap.backend.domain.dto.response.roadmap.StudentRoadmapResponse
    getStudentRoadmap(String authHeader) {
        Student student = getStudentFromAuthHeader(authHeader);
        CareerRole careerRole = student.getCareerRole();
        if (careerRole == null) {
            throw new ResourceNotFoundException("Student has not selected a career path yet.");
        }

        List<SkillNode> nodes = skillNodeRepository
                .findByCareerRole_CareerIdOrderByLevelAscNodeNameAsc(careerRole.getCareerId());
        List<StudentProgress> progressList = studentProgressRepository.findByStudent(student);
        Map<UUID, String> progressMap = progressList.stream()
                .collect(Collectors.toMap(
                        progress -> progress.getSkillNode().getNodeId(),
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
    public com.inteliroadmap.backend.domain.dto.response.roadmap.StudentRoadmapResponse
    getCareerRoadmapTemplate(UUID careerId) {
        Optional<CareerRole> careerRoleOptional = careerRoleRepository.findById(careerId);
        if (careerRoleOptional.isEmpty()) {
            throw new ResourceNotFoundException("Career not found");
        }

        CareerRole careerRole = careerRoleOptional.get();
        List<SkillNode> nodes = skillNodeRepository
                .findByCareerRole_CareerIdOrderByLevelAscNodeNameAsc(careerId);
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
    public void updateNodeProgress(UpdateNodeProgressRequest request, String authHeader) {
        Student student = getStudentFromAuthHeader(authHeader);

        Optional<SkillNode> nodeOptional = skillNodeRepository.findById(request.getNodeId());
        if (nodeOptional.isEmpty()) {
            throw new ResourceNotFoundException("Node not found");
        }

        SkillNode node = nodeOptional.get();
        Optional<StudentProgress> progressOptional =
                studentProgressRepository.findByStudentAndSkillNode(student, node);

        StudentProgress progress;
        if (progressOptional.isPresent()) {
            progress = progressOptional.get();
        } else {
            progress = StudentProgress.builder()
                    .student(student)
                    .skillNode(node)
                    .createAt(LocalDateTime.now())
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
                && progress.getCompleteAt() == null) {
            progress.setCompleteAt(java.time.LocalDateTime.now());
            
            // Auto-sync skill to profile
            if (node.getSkillId() != null) {
                Skill skill = skillRepository.findById(node.getSkillId()).orElse(null);
                if (skill != null) {
                    boolean hasSkill = studentSkillRepository.existsByStudentAndSkill(student, skill);
                    if (!hasSkill) {
                        List<SkillNode> allNodesForSkill = skillNodeRepository.findBySkillIdAndCareerRole_CareerId(skill.getSkillId(), student.getCareerRole().getCareerId());
                        boolean allCompleted = true;
                        
                        for (SkillNode skillNode : allNodesForSkill) {
                            if (skillNode.getNodeId().equals(node.getNodeId())) {
                                continue;
                            }
                            StudentProgress nodeProgress = studentProgressRepository.findByStudentAndSkillNode(student, skillNode).orElse(null);
                            if (nodeProgress == null || nodeProgress.getStatus() != RoadmapStepStatus.COMPLETED) {
                                allCompleted = false;
                                break;
                            }
                        }

                        if (allCompleted) {
                            StudentSkill newSkill = StudentSkill.builder()
                                    .student(student)
                                    .skill(skill)
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
    public SkillGapResponse compareSkills(String authHeader) {
        Student student = getStudentFromAuthHeader(authHeader);
        CareerRole careerRole = student.getCareerRole();
        if (careerRole == null) {
            throw new ResourceNotFoundException("Student has not selected a career path yet.");
        }

        List<StudentSkill> currentStudentSkills = studentSkillRepository.findByStudent(student);
        Set<String> currentSkillNames = currentStudentSkills.stream()
                .map(studentSkill -> studentSkill.getSkill().getSkillName())
                .collect(Collectors.toSet());

        List<CareerRequiredSkill> requiredSkills =
                careerRequiredSkillRepository.findByCareerRole(careerRole);
        List<String> missingSkills = requiredSkills.stream()
                .map(requiredSkill -> requiredSkill.getSkill().getSkillName())
                .filter(skillName -> !currentSkillNames.contains(skillName))
                .toList();

        return SkillGapResponse.builder()
                .currentSkills(new ArrayList<>(currentSkillNames))
                .missingSkills(missingSkills)
                .build();
    }

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

    private RoadmapNodeDto mapToLegacyNodeDto(SkillNode node, String status) {
        String childNodeOf = node.getChildNodeOf() != null
                ? node.getChildNodeOf().getNodeName()
                : null;

        return RoadmapNodeDto.builder()
                .nodeId(node.getNodeId())
                .nodeName(node.getNodeName())
                .subtreeName(node.getSubtreeName())
                .childNodeOf(childNodeOf)
                .level(node.getLevel())
                .description(node.getDescription())
                .resource(node.getResource())
                .status(status != null ? status : "not_started")
                .build();
    }

    private Map<UUID, StudentProgress> mapProgressByNodeId(List<StudentProgress> progresses) {
        Map<UUID, StudentProgress> progressByNodeId = new HashMap<>();
        for (StudentProgress progress : progresses) {
            if (progress.getSkillNode() != null) {
                progressByNodeId.put(progress.getSkillNode().getNodeId(), progress);
            }
        }
        return progressByNodeId;
    }

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

            if (node.getChildNodeOf() == null) {
                statusByNodeId.put(node.getNodeId(), FRONTEND_CURRENT_STATUS);
            } else {
                String parentStatus = statusByNodeId.get(node.getChildNodeOf().getNodeId());
                if (FRONTEND_COMPLETED_STATUS.equals(parentStatus)) {
                    statusByNodeId.put(node.getNodeId(), FRONTEND_CURRENT_STATUS);
                } else {
                    statusByNodeId.put(node.getNodeId(), FRONTEND_LOCKED_STATUS);
                }
            }
        }

        return statusByNodeId;
    }

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

    private List<StudentRoadmapNodeResponse> buildRoadmapTree(
            List<SkillNode> nodes,
            Map<UUID, String> statusByNodeId
    ) {
        Map<UUID, List<SkillNode>> childrenByParentId = new HashMap<>();
        List<SkillNode> rootNodes = new ArrayList<>();

        for (SkillNode node : nodes) {
            if (node.getChildNodeOf() == null) {
                rootNodes.add(node);
                continue;
            }

            UUID parentId = node.getChildNodeOf().getNodeId();
            childrenByParentId.computeIfAbsent(parentId, key -> new ArrayList<>()).add(node);
        }

        return rootNodes.stream()
                .map(node -> toRoadmapNodeResponse(node, statusByNodeId, childrenByParentId))
                .toList();
    }

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
                .level(node.getLevel())
                .childNodeOf(node.getChildNodeOf() != null ? node.getChildNodeOf().getNodeId() : null)
                .resources(toResourceResponses(node.getResource()))
                .children(children)
                .build();
    }

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

    private StudentRoadmapResourceResponse toResourceResponse(String url) {
        return StudentRoadmapResourceResponse.builder()
                .title(url)
                .url(url)
                .type("article")
                .build();
    }

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

    private Object firstNonNull(Object first, Object second) {
        if (first != null) {
            return first;
        }
        return second;
    }
}
