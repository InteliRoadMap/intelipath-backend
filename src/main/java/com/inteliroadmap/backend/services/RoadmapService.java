package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.response.RoadmapResponse;
import com.inteliroadmap.backend.domain.entity.*;
import com.inteliroadmap.backend.repositories.*;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoadmapService {

    private final StudentRepository studentRepository;
    private final CareerRoleRepository careerRoleRepository;
    private final SkillNodeRepository skillNodeRepository;
    private final StudentProgressRepository studentProgressRepository;

    private Student getAuthenticatedStudent() {
        return studentRepository.findAll().getFirst();
    }

    @Transactional(readOnly = true)
    public RoadmapResponse getRoadmap(UUID careerId) {
        log.info("Roadmap Module: Fetching roadmap for career ID: {}", careerId);
        
        Student student = getAuthenticatedStudent();
        CareerRole careerRole = careerRoleRepository.findById(careerId)
                .orElseThrow(() -> new ResourceNotFoundException("Career role not found"));
                
        List<SkillNode> nodes = skillNodeRepository.findByCareerRole_CareerId(careerId);
        List<StudentProgress> progresses = studentProgressRepository.findByStudent(student);

        return RoadmapResponse.builder()
                .skillNodes(nodes)
                .progresses(progresses)
                .build();
    }

    public RoadmapResponse getNode(UUID nodeId) {
        log.info("Roadmap Module: Fetching Node information");

        SkillNode node = skillNodeRepository.findById(nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Node not found"));

        return RoadmapResponse.builder()
                .skillNode(node)
                .build();
    }

    @Transactional
    public RoadmapResponse updateNodeProgress(UUID nodeId) {
        log.info("Roadmap Module: Updating Node progress");

        Student student = getAuthenticatedStudent();
        SkillNode node = skillNodeRepository.findById(nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Node not found"));
                
        StudentProgress progress = studentProgressRepository.findByStudentAndSkillNode(student, node)
                .orElseThrow(() -> new ResourceNotFoundException("Student progress not found for this node"));
                
        if("in_progress".equals(progress.getStatus())){
            progress.setStatus("completed");
            progress.setCompleteAt(LocalDateTime.now());
            studentProgressRepository.save(progress);
        }
        
        return RoadmapResponse.builder()
                .updateStatus("Update Node status successfully")
                .build();
    }
}
