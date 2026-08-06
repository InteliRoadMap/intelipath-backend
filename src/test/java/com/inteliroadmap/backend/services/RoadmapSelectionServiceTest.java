package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.SelectAlternativeRequest;
import com.inteliroadmap.backend.domain.dto.response.roadmap.NodeSelectionResponse;
import com.inteliroadmap.backend.domain.entity.CareerRole;
import com.inteliroadmap.backend.domain.entity.SkillNode;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.StudentNodeSelection;
import com.inteliroadmap.backend.exceptions.BadRequestException;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.repositories.SkillNodeRepository;
import com.inteliroadmap.backend.repositories.StudentNodeSelectionRepository;
import com.inteliroadmap.backend.repositories.StudentSkillRepository;
import com.inteliroadmap.backend.components.StackBranchScorer;
import com.inteliroadmap.backend.services.MarketDemandService;
import com.inteliroadmap.backend.services.impl.RoadmapSelectionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoadmapSelectionServiceTest {

    @Mock
    private AuthenticatedStudentService authenticatedStudentService;
    @Mock
    private StudentNodeSelectionRepository selectionRepository;
    @Mock
    private SkillNodeRepository skillNodeRepository;
    @Mock
    private StudentSkillRepository studentSkillRepository;
    @Mock
    private MarketDemandService marketDemandService;

    private RoadmapSelectionService selectionService;

    private Student student;
    private CareerRole career;
    private SkillNode group;
    private SkillNode java;
    private SkillNode csharp;

    @BeforeEach
    void setUp() {
        // The scorer is the real one: it is a pure function of the arguments, and
        // mocking it here would test the wiring against a fiction.
        selectionService = new RoadmapSelectionServiceImpl(
                authenticatedStudentService, selectionRepository, skillNodeRepository,
                studentSkillRepository, new StackBranchScorer(), marketDemandService);

        career = CareerRole.builder().careerId(UUID.randomUUID()).careerName("Backend").build();
        student = Student.builder().userId(UUID.randomUUID()).careerRole(career).build();

        group = SkillNode.builder()
                .nodeId(UUID.randomUUID())
                .nodeName("Pick a Language")
                .selection("CHOOSE_ONE")
                .careerRole(career)
                .build();
        java = SkillNode.builder()
                .nodeId(UUID.randomUUID())
                .nodeName("Java")
                .parentNode(group)
                .careerRole(career)
                .build();
        csharp = SkillNode.builder()
                .nodeId(UUID.randomUUID())
                .nodeName("C#")
                .parentNode(group)
                .careerRole(career)
                .build();
    }

    private SelectAlternativeRequest request(SkillNode chosen) {
        return SelectAlternativeRequest.builder()
                .groupNodeId(group.getNodeId())
                .chosenNodeId(chosen.getNodeId())
                .build();
    }

    @Test
    void selectAlternative_createsSelection() {
        when(authenticatedStudentService.getRequiredStudent()).thenReturn(student);
        when(skillNodeRepository.findById(group.getNodeId())).thenReturn(Optional.of(group));
        when(skillNodeRepository.findById(java.getNodeId())).thenReturn(Optional.of(java));
        when(selectionRepository.findByStudent_UserIdAndGroupNode_NodeId(student.getUserId(), group.getNodeId()))
                .thenReturn(Optional.empty());
        when(selectionRepository.save(any(StudentNodeSelection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NodeSelectionResponse response = selectionService.selectAlternative(request(java));

        assertEquals(group.getNodeId(), response.getGroupNodeId());
        assertEquals(java.getNodeId(), response.getChosenNodeId());
        assertEquals("Java", response.getChosenNodeName());

        ArgumentCaptor<StudentNodeSelection> captor = ArgumentCaptor.forClass(StudentNodeSelection.class);
        verify(selectionRepository).save(captor.capture());
        assertEquals(student.getUserId(), captor.getValue().getStudent().getUserId());
        assertEquals(java.getNodeId(), captor.getValue().getChosenNode().getNodeId());
    }

    @Test
    void selectAlternative_rePickSwitchesExistingSelection() {
        StudentNodeSelection existing = StudentNodeSelection.builder()
                .selectionId(UUID.randomUUID())
                .student(student)
                .groupNode(group)
                .chosenNode(csharp)
                .build();

        when(authenticatedStudentService.getRequiredStudent()).thenReturn(student);
        when(skillNodeRepository.findById(group.getNodeId())).thenReturn(Optional.of(group));
        when(skillNodeRepository.findById(java.getNodeId())).thenReturn(Optional.of(java));
        when(selectionRepository.findByStudent_UserIdAndGroupNode_NodeId(student.getUserId(), group.getNodeId()))
                .thenReturn(Optional.of(existing));
        when(selectionRepository.save(any(StudentNodeSelection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NodeSelectionResponse response = selectionService.selectAlternative(request(java));

        // Same row updated in place (upsert), now pointing at Java.
        assertEquals(java.getNodeId(), response.getChosenNodeId());
        ArgumentCaptor<StudentNodeSelection> captor = ArgumentCaptor.forClass(StudentNodeSelection.class);
        verify(selectionRepository).save(captor.capture());
        assertEquals(existing.getSelectionId(), captor.getValue().getSelectionId());
        assertEquals(java.getNodeId(), captor.getValue().getChosenNode().getNodeId());
    }

    @Test
    void selectAlternative_rejectsNodeOutsideGroup() {
        SkillNode stranger = SkillNode.builder()
                .nodeId(UUID.randomUUID())
                .nodeName("Docker")
                .parentNode(SkillNode.builder().nodeId(UUID.randomUUID()).build())
                .build();

        when(authenticatedStudentService.getRequiredStudent()).thenReturn(student);
        when(skillNodeRepository.findById(group.getNodeId())).thenReturn(Optional.of(group));
        when(skillNodeRepository.findById(stranger.getNodeId())).thenReturn(Optional.of(stranger));

        assertThrows(BadRequestException.class,
                () -> selectionService.selectAlternative(request(stranger)));
        verify(selectionRepository, never()).save(any());
    }

    @Test
    void selectAlternative_rejectsNonChooseOneGroup() {
        group.setSelection("ALL");
        when(authenticatedStudentService.getRequiredStudent()).thenReturn(student);
        when(skillNodeRepository.findById(group.getNodeId())).thenReturn(Optional.of(group));

        assertThrows(BadRequestException.class,
                () -> selectionService.selectAlternative(request(java)));
        verify(selectionRepository, never()).save(any());
    }

    @Test
    void selectAlternative_rejectsGroupFromAnotherCareer() {
        group.setCareerRole(CareerRole.builder().careerId(UUID.randomUUID()).careerName("Frontend").build());
        when(authenticatedStudentService.getRequiredStudent()).thenReturn(student);
        when(skillNodeRepository.findById(group.getNodeId())).thenReturn(Optional.of(group));

        assertThrows(BadRequestException.class,
                () -> selectionService.selectAlternative(request(java)));
        verify(selectionRepository, never()).save(any());
    }

    @Test
    void clearSelection_missingSelectionThrowsNotFound() {
        when(authenticatedStudentService.getRequiredStudent()).thenReturn(student);
        when(selectionRepository.findByStudent_UserIdAndGroupNode_NodeId(student.getUserId(), group.getNodeId()))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> selectionService.clearSelection(group.getNodeId()));
    }
}
