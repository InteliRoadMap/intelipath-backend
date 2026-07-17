package com.inteliroadmap.backend.services.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.inteliroadmap.backend.domain.dto.request.SelectAlternativeRequest;
import com.inteliroadmap.backend.domain.dto.response.roadmap.NodeSelectionResponse;
import com.inteliroadmap.backend.domain.entity.SkillNode;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.StudentNodeSelection;
import com.inteliroadmap.backend.domain.entity.StudentSkill;
import com.inteliroadmap.backend.exceptions.BadRequestException;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.repositories.SkillNodeRepository;
import com.inteliroadmap.backend.repositories.StudentNodeSelectionRepository;
import com.inteliroadmap.backend.repositories.StudentSkillRepository;
import com.inteliroadmap.backend.services.AuthenticatedStudentService;
import com.inteliroadmap.backend.services.RoadmapSelectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of {@link RoadmapSelectionService}.
 *
 * Selection history note: switching a choice does NOT delete the old branch's
 * student_progress rows. Completed work stays recorded; the roadmap read path
 * simply stops counting the now-unchosen branch, so switching back later
 * restores the earlier progress.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoadmapSelectionServiceImpl implements RoadmapSelectionService {

    private static final String CHOOSE_ONE = "CHOOSE_ONE";

    private final AuthenticatedStudentService authenticatedStudentService;
    private final StudentNodeSelectionRepository selectionRepository;
    private final SkillNodeRepository skillNodeRepository;
    private final StudentSkillRepository studentSkillRepository;

    @Transactional
    @Override
    public NodeSelectionResponse selectAlternative(SelectAlternativeRequest request) {
        log.info("RoadmapSelectionServiceImpl: Select alternative request received. group: {}, chosen: {}",
                request.getGroupNodeId(), request.getChosenNodeId());

        Student student = authenticatedStudentService.getRequiredStudent();
        SkillNode group = getChoiceGroupOwnedByStudentCareer(request.getGroupNodeId(), student);

        SkillNode chosen = skillNodeRepository.findById(request.getChosenNodeId())
                .orElseThrow(() -> new ResourceNotFoundException("Chosen node not found"));
        if (chosen.getParentNode() == null
                || !group.getNodeId().equals(chosen.getParentNode().getNodeId())) {
            throw new BadRequestException("Chosen node is not an alternative of the given group");
        }

        // Upsert on (student, group): re-picking switches the stored choice.
        StudentNodeSelection selection = selectionRepository
                .findByStudent_UserIdAndGroupNode_NodeId(student.getUserId(), group.getNodeId())
                .orElseGet(() -> StudentNodeSelection.builder()
                        .student(Student.builder().userId(student.getUserId()).build())
                        .groupNode(group)
                        .build());
        selection.setChosenNode(chosen);
        selection = selectionRepository.save(selection);

        log.info("RoadmapSelectionServiceImpl: Student {} chose '{}' in group '{}'",
                student.getUserId(), chosen.getNodeName(), group.getNodeName());
        return toResponse(selection, group, chosen);
    }

    @Transactional(readOnly = true)
    @Override
    public List<NodeSelectionResponse> getSelections() {
        Student student = authenticatedStudentService.getRequiredStudent();
        return selectionRepository.findByStudent_UserId(student.getUserId()).stream()
                .map(selection -> toResponse(selection, selection.getGroupNode(), selection.getChosenNode()))
                .toList();
    }

    @Transactional
    @Override
    public void clearSelection(UUID groupNodeId) {
        log.info("RoadmapSelectionServiceImpl: Clear selection request received. group: {}", groupNodeId);

        Student student = authenticatedStudentService.getRequiredStudent();
        StudentNodeSelection selection = selectionRepository
                .findByStudent_UserIdAndGroupNode_NodeId(student.getUserId(), groupNodeId)
                .orElseThrow(() -> new ResourceNotFoundException("No selection stored for this group"));
        selectionRepository.delete(selection);
    }

    /**
     * Runs in its own write transaction because the roadmap read path (a
     * read-only transaction) triggers it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public int autoDefaultSelections() {
        Student student = authenticatedStudentService.getRequiredStudent();
        if (student.getCareerRole() == null || student.getCareerRole().getCareerId() == null) {
            return 0;
        }

        List<SkillNode> careerNodes = skillNodeRepository
                .findByCareerRole_CareerId(student.getCareerRole().getCareerId());
        if (careerNodes.isEmpty()) {
            return 0;
        }

        Set<UUID> alreadyChosenGroups = new HashSet<>();
        for (StudentNodeSelection selection : selectionRepository.findByStudent_UserId(student.getUserId())) {
            alreadyChosenGroups.add(selection.getGroupNode().getNodeId());
        }

        Set<String> skillNames = new HashSet<>();
        for (StudentSkill studentSkill : studentSkillRepository.findByStudent_UserId(student.getUserId())) {
            if (studentSkill.getSkill() != null && studentSkill.getSkill().getSkillName() != null) {
                skillNames.add(studentSkill.getSkill().getSkillName().toLowerCase(Locale.ROOT));
            }
        }
        if (skillNames.isEmpty()) {
            return 0;
        }

        // Children of each CHOOSE_ONE group that has no stored selection yet.
        Map<UUID, SkillNode> groupsById = new HashMap<>();
        Map<UUID, List<SkillNode>> alternativesByGroup = new HashMap<>();
        for (SkillNode node : careerNodes) {
            if (CHOOSE_ONE.equalsIgnoreCase(node.getSelection())
                    && !alreadyChosenGroups.contains(node.getNodeId())) {
                groupsById.put(node.getNodeId(), node);
            }
        }
        for (SkillNode node : careerNodes) {
            if (node.getParentNode() != null && groupsById.containsKey(node.getParentNode().getNodeId())) {
                alternativesByGroup
                        .computeIfAbsent(node.getParentNode().getNodeId(), key -> new ArrayList<>())
                        .add(node);
            }
        }

        int created = 0;
        for (Map.Entry<UUID, List<SkillNode>> entry : alternativesByGroup.entrySet()) {
            List<SkillNode> matches = entry.getValue().stream()
                    .filter(alternative -> matchesStudentSkill(alternative, skillNames))
                    .toList();
            // Only act on an unambiguous signal; zero or multiple matches keep
            // the decision with the student.
            if (matches.size() != 1) {
                continue;
            }

            SkillNode group = groupsById.get(entry.getKey());
            SkillNode chosen = matches.get(0);
            selectionRepository.save(StudentNodeSelection.builder()
                    .student(Student.builder().userId(student.getUserId()).build())
                    .groupNode(group)
                    .chosenNode(chosen)
                    .build());
            created++;
            log.info("RoadmapSelectionServiceImpl: Auto-selected '{}' in group '{}' for student {} (skill profile match)",
                    chosen.getNodeName(), group.getNodeName(), student.getUserId());
        }
        return created;
    }

    /** True when the alternative's name or one of its evidence keywords is a profile skill. */
    private boolean matchesStudentSkill(SkillNode alternative, Set<String> skillNames) {
        if (alternative.getNodeName() != null
                && skillNames.contains(alternative.getNodeName().toLowerCase(Locale.ROOT))) {
            return true;
        }
        JsonNode keywords = alternative.getEvidenceKeywords();
        if (keywords != null && keywords.isArray()) {
            for (JsonNode keyword : keywords) {
                if (skillNames.contains(keyword.asText("").toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Loads the group node and validates it is a CHOOSE_ONE group belonging to
     * the student's selected career.
     */
    private SkillNode getChoiceGroupOwnedByStudentCareer(UUID groupNodeId, Student student) {
        SkillNode group = skillNodeRepository.findById(groupNodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Group node not found"));

        if (!CHOOSE_ONE.equalsIgnoreCase(group.getSelection())) {
            throw new BadRequestException("Node '" + group.getNodeName() + "' is not a choose-one group");
        }
        if (student.getCareerRole() == null || student.getCareerRole().getCareerId() == null) {
            throw new BadRequestException("Student has not selected a career path");
        }
        if (group.getCareerRole() == null
                || !student.getCareerRole().getCareerId().equals(group.getCareerRole().getCareerId())) {
            throw new BadRequestException("Group node does not belong to the student's career roadmap");
        }
        return group;
    }

    private NodeSelectionResponse toResponse(StudentNodeSelection selection, SkillNode group, SkillNode chosen) {
        return NodeSelectionResponse.builder()
                .groupNodeId(group.getNodeId())
                .groupNodeName(group.getNodeName())
                .chosenNodeId(chosen.getNodeId())
                .chosenNodeName(chosen.getNodeName())
                .createdAt(selection.getCreatedAt())
                .build();
    }
}
