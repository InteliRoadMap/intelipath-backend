package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.components.CoreSkillEligibility;
import com.inteliroadmap.backend.domain.dto.request.ImportSkillsRequest;
import com.inteliroadmap.backend.domain.entity.CareerRole;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.StudentSkill;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.services.AuthenticatedStudentService;
import com.inteliroadmap.backend.services.impl.SkillServiceImpl;
import com.inteliroadmap.backend.mappers.SkillMapper;
import com.inteliroadmap.backend.repositories.CareerRequiredSkillRepository;
import com.inteliroadmap.backend.repositories.CareerRoleRepository;
import com.inteliroadmap.backend.repositories.SkillRepository;
import com.inteliroadmap.backend.repositories.StudentSkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillServiceTest {

    @Mock
    private SkillRepository skillRepository;
    @Mock
    private StudentSkillRepository studentSkillRepository;
    @Mock
    private CareerRoleRepository careerRoleRepository;
    @Mock
    private CareerRequiredSkillRepository careerRequiredSkillRepository;
    @Mock
    private AuthenticatedStudentService authenticatedStudentService;
    @Mock
    private SkillEvidenceService skillEvidenceService;

    private SkillService skillService;

    @BeforeEach
    void setUp() {
        skillService = new SkillServiceImpl(
                skillRepository,
                studentSkillRepository,
                careerRoleRepository,
                careerRequiredSkillRepository,
                authenticatedStudentService,
                skillEvidenceService,
                new SkillMapper(skillRepository),
                // Real, not mocked: the picker's name gate is the thing that keeps
                // `$elemMatch` out of the list a student declares from, and a mock would
                // let a regression in it pass unnoticed here.
                new CoreSkillEligibility()
        );
    }

    @Test
    void importStudentSkillsSavesUniqueNewSkills() {
        Student student = Student.builder().userId(UUID.randomUUID()).build();
        Skill skill = skill("Java");
        ImportSkillsRequest request = request(skill.getSkillId(), skill.getSkillId());

        when(authenticatedStudentService.getOrCreateStudentForUpdate()).thenReturn(student);
        when(skillRepository.findAllById(anySet())).thenReturn(List.of(skill));
        when(studentSkillRepository.findByStudent_UserIdAndSkill_SkillIdIn(student.getUserId(), List.of(skill.getSkillId())))
                .thenReturn(List.of());
        when(studentSkillRepository.findByStudent_UserId(student.getUserId()))
                .thenReturn(List.of(StudentSkill.builder().student(student).skill(skill).build()));
        when(skillRepository.findById(skill.getSkillId())).thenReturn(Optional.of(skill));

        var response = skillService.importStudentSkills(request);

        ArgumentCaptor<List<StudentSkill>> captor = ArgumentCaptor.forClass(List.class);
        verify(studentSkillRepository).saveAll(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(skill.getSkillId(), response.getSelectedSkills().getFirst().getSkillId());
        assertAllListsNotNull(response);
    }

    @Test
    void importStudentSkillsDoesNotInsertExistingSkill() {
        Student student = Student.builder().userId(UUID.randomUUID()).build();
        Skill skill = skill("Java");
        StudentSkill existing = StudentSkill.builder().student(student).skill(skill).build();

        when(authenticatedStudentService.getOrCreateStudentForUpdate()).thenReturn(student);
        when(skillRepository.findAllById(anySet())).thenReturn(List.of(skill));
        when(studentSkillRepository.findByStudent_UserIdAndSkill_SkillIdIn(student.getUserId(), List.of(skill.getSkillId())))
                .thenReturn(List.of(existing));
        when(studentSkillRepository.findByStudent_UserId(student.getUserId())).thenReturn(List.of(existing));
        when(skillRepository.findById(skill.getSkillId())).thenReturn(Optional.of(skill));

        var response = skillService.importStudentSkills(request(skill.getSkillId()));

        verify(studentSkillRepository, never()).saveAll(anyList());
        assertEquals(1, response.getSelectedSkills().size());
        assertAllListsNotNull(response);
    }

    @Test
    void importStudentSkillsRejectsEntireRequestWhenSkillIsMissing() {
        Student student = Student.builder().userId(UUID.randomUUID()).build();
        Skill skill = skill("Java");
        UUID missingSkillId = UUID.randomUUID();
        ImportSkillsRequest request = request(skill.getSkillId(), missingSkillId);

        when(authenticatedStudentService.getOrCreateStudentForUpdate()).thenReturn(student);
        when(skillRepository.findAllById(anySet())).thenReturn(List.of(skill));

        assertThrows(ResourceNotFoundException.class, () -> skillService.importStudentSkills(request));
        verify(studentSkillRepository, never()).saveAll(anyList());
    }

    @Test
    void getStudentSkillsReturnsSelectedAndAllAvailableSkills() {
        Student student = Student.builder().userId(UUID.randomUUID()).careerRole(CareerRole.builder().build()).build();
        Skill selectedSkill = skill("Java");
        Skill availableSkill = skill("Python");
        StudentSkill selected = StudentSkill.builder().student(student).skill(selectedSkill).build();

        when(authenticatedStudentService.getOrCreateStudent()).thenReturn(student);
        when(studentSkillRepository.findByStudent_UserId(student.getUserId())).thenReturn(List.of(selected));
        when(skillRepository.findDeclarableCandidates()).thenReturn(List.of(selectedSkill, availableSkill));
        when(skillRepository.findById(selectedSkill.getSkillId())).thenReturn(Optional.of(selectedSkill));

        var response = skillService.getStudentSkills();

        assertEquals(1, response.getSelectedSkills().size());
        assertEquals(2, response.getSkills().size());
        assertAllListsNotNull(response);
    }

    @Test
    void getStudentSkillsReturnsEmptySelectedSkillsWhenStudentHasNone() {
        Student student = Student.builder().userId(UUID.randomUUID()).careerRole(CareerRole.builder().build()).build();
        Skill availableSkill = skill("Java");

        when(authenticatedStudentService.getOrCreateStudent()).thenReturn(student);
        when(studentSkillRepository.findByStudent_UserId(student.getUserId())).thenReturn(List.of());
        when(skillRepository.findDeclarableCandidates()).thenReturn(List.of(availableSkill));

        var response = skillService.getStudentSkills();

        assertEquals(0, response.getSelectedSkills().size());
        assertEquals(1, response.getSkills().size());
        assertAllListsNotNull(response);
    }

    @Test
    void searchSkillsSearchesBySkillName() {
        // Search runs over the declarable population, not the raw catalog: `$match`
        // matches the substring "ma" and must not come back from a search either.
        when(skillRepository.findDeclarableCandidates())
                .thenReturn(List.of(skill("Java"), skill("Python")));

        var response = skillService.searchSkills("ja");

        assertEquals(1, response.getSkills().size());
        assertEquals("Java", response.getSkills().getFirst().getSkillName());
        assertAllListsNotNull(response);
    }

    @Test
    void searchSkillsReturnsEmptyListsWhenNoSkillMatches() {
        when(skillRepository.findDeclarableCandidates()).thenReturn(List.of(skill("Java")));

        var response = skillService.searchSkills("missing");

        assertEquals(0, response.getSkills().size());
        assertAllListsNotNull(response);
    }

    /**
     * The bug the student saw: the picker asked for the whole catalog, and the catalog is
     * also every node title of every imported roadmap — so a student was offered
     * {@code $elemMatch}, {@code --watch} and {@code @else if} to declare, sorted above
     * {@code Android}.
     *
     * <p>Nothing here is deleted from the catalog. These rows are still nodes inside the
     * MongoDB and Sass tracks, and still things to learn. They are just not things a
     * person says they can do.
     */
    @Test
    void thePickerDoesNotOfferRoadmapNodeTitlesToDeclare() {
        Student student = Student.builder().userId(UUID.randomUUID()).build();

        when(authenticatedStudentService.getOrCreateStudent()).thenReturn(student);
        when(studentSkillRepository.findByStudent_UserId(student.getUserId())).thenReturn(List.of());
        when(skillRepository.findDeclarableCandidates()).thenReturn(List.of(
                skill("Android"), skill("$elemMatch"), skill("--watch"), skill("@else if"),
                skill("Testing Methodologies & Techniques"), skill("Cloud"), skill(".NET")));

        var names = skillService.getStudentSkills().getSkills().stream()
                .map(com.inteliroadmap.backend.domain.dto.response.roadmap.SkillItemResponse::getSkillName)
                .toList();

        assertEquals(List.of("Android", ".NET"), names);
    }

    private void assertAllListsNotNull(com.inteliroadmap.backend.domain.dto.response.roadmap.SkillResponse response) {
        assertNotNull(response.getSelectedSkills());
        assertNotNull(response.getSkills());
        assertNotNull(response.getRequiredSkills());
        assertNotNull(response.getMissingSkills());
    }

    private ImportSkillsRequest request(UUID... skillIds) {
        ImportSkillsRequest request = new ImportSkillsRequest();
        request.setSkillIds(List.of(skillIds));
        return request;
    }

    private Skill skill(String name) {
        return Skill.builder()
                .skillId(UUID.randomUUID())
                .skillName(name)
                .category("Backend")
                .build();
    }
}
