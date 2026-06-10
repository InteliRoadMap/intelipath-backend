package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.UpdateUserProgressRequest;
import com.inteliroadmap.backend.domain.dto.response.RoadmapResponse;
import com.inteliroadmap.backend.domain.entity.*;
import com.inteliroadmap.backend.repositories.*;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoadmapService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final CareerRoleRepository careerRoleRepository;
    private final SkillNodeRepository skillNodeRepository;
    private final StudentProgressRepository studentProgressRepository;
    private final com.inteliroadmap.backend.helper.AuthenticatedStudentHelper authenticatedStudentHelper;

    /**
     * Securely identifies the currently authenticated student.
     * Extracts the user email from the SecurityContextHolder and retrieves the corresponding Student profile.
     *
     * @return The authenticated Student entity
     * @throws ResourceNotFoundException if the token is invalid or the student profile is missing
     */
    private Student getAuthenticatedStudent() {
        return authenticatedStudentHelper.getOrCreateStudent();
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
                .filter(p -> "COMPLETED".equals(p.getStatus()))
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
                
        if("IN_PROGRESS".equals(progress.getStatus())){
            progress.setStatus("COMPLETED");
            progress.setCompleteAt(LocalDateTime.now());
            studentProgressRepository.save(progress);
        }
        
        return RoadmapResponse.builder()
                .updateStatus("Update Node status successfully")
                .build();
    }
}
