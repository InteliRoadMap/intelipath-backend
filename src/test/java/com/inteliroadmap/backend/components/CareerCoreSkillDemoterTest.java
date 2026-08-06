package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.repositories.CareerRequiredSkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CareerCoreSkillDemoterTest {

    @Mock
    private CareerRequiredSkillRepository careerRequiredSkillRepository;

    private CareerCoreSkillDemoter demoter;

    @BeforeEach
    void setUp() {
        demoter = new CareerCoreSkillDemoter(careerRequiredSkillRepository, new CoreSkillEligibility());
    }

    @Test
    void aSkillTheMarketAsksForByNameIsLeftAlone() {
        UUID row = UUID.randomUUID();
        when(careerRequiredSkillRepository.findHighRowsWithEvidence())
                .thenReturn(List.<Object[]>of(highRow(row, "Spring Boot", 52, 4, 0)));

        assertEquals(0, demoter.demoteUnmeasurableCoreSkills());
        verify(careerRequiredSkillRepository, never()).demoteToAvg(anyCollection());
    }

    @Test
    void aSkillWithNoPostingsButATaughtLeafNodeStaysHigh() {
        // Silence is not a demotion. bcrypt and OWASP are real backend topics no
        // Vietnamese advert spells out, and a roadmap teaches them as leaves.
        UUID row = UUID.randomUUID();
        when(careerRequiredSkillRepository.findHighRowsWithEvidence())
                .thenReturn(List.<Object[]>of(highRow(row, "bcrypt", 0, 2, 0)));

        assertEquals(0, demoter.demoteUnmeasurableCoreSkills());
    }

    @Test
    void aContainerNodeIsDemotedEvenThoughItsNameIsWellFormed() {
        // "Package Managers" reads like a skill and has eight children. The student is
        // measured on npm and pnpm, not on the folder they sit in.
        UUID row = UUID.randomUUID();
        when(careerRequiredSkillRepository.findHighRowsWithEvidence())
                .thenReturn(List.<Object[]>of(highRow(row, "Package Managers", 0, 5, 8)));
        when(careerRequiredSkillRepository.demoteToAvg(anyCollection())).thenReturn(1);

        assertEquals(1, demoter.demoteUnmeasurableCoreSkills());
        assertTrue(demotedIds().contains(row));
    }

    @Test
    void aRowOnNoRoadmapThatNoPostingNamesIsDemoted() {
        UUID row = UUID.randomUUID();
        when(careerRequiredSkillRepository.findHighRowsWithEvidence())
                .thenReturn(List.<Object[]>of(highRow(row, "Architect Soft Skills", 0, 0, 0)));
        when(careerRequiredSkillRepository.demoteToAvg(anyCollection())).thenReturn(1);

        assertEquals(1, demoter.demoteUnmeasurableCoreSkills());
    }

    @Test
    void aSkillOnNoRoadmapThatTheMarketDoesAskForStaysHigh() {
        // Note the AND in the rule. This row is a gap in the curriculum, not junk, and
        // demoting it would hide the very thing the roadmap needs to be told about.
        UUID row = UUID.randomUUID();
        when(careerRequiredSkillRepository.findHighRowsWithEvidence())
                .thenReturn(List.<Object[]>of(highRow(row, "Terraform", 24, 0, 0)));

        assertEquals(0, demoter.demoteUnmeasurableCoreSkills());
    }

    @Test
    void aCategoryWordIsDemotedHoweverMuchEvidenceItHas() {
        // Measured this run: DevOps 132 postings, Cloud 84, API 80. Evidence is not the
        // question - "can a student be at Practiced in Cloud" is, and the answer is no.
        UUID devops = UUID.randomUUID();
        UUID cloud = UUID.randomUUID();
        when(careerRequiredSkillRepository.findHighRowsWithEvidence())
                .thenReturn(List.<Object[]>of(
                        highRow(devops, "DevOps", 132, 3, 0),
                        highRow(cloud, "Cloud", 84, 1, 0)));
        when(careerRequiredSkillRepository.demoteToAvg(anyCollection())).thenReturn(2);

        assertEquals(2, demoter.demoteUnmeasurableCoreSkills());
        assertTrue(demotedIds().containsAll(List.of(devops, cloud)));
    }

    @Test
    void demotionNeverTouchesRowsThatPass() {
        UUID keep = UUID.randomUUID();
        UUID drop = UUID.randomUUID();
        when(careerRequiredSkillRepository.findHighRowsWithEvidence())
                .thenReturn(List.<Object[]>of(
                        highRow(keep, "PostgreSQL", 53, 6, 0),
                        highRow(drop, "Cloud Computing & AWS", 0, 0, 0)));
        when(careerRequiredSkillRepository.demoteToAvg(anyCollection())).thenReturn(1);

        demoter.demoteUnmeasurableCoreSkills();
        Collection<UUID> demoted = demotedIds();
        assertTrue(demoted.contains(drop));
        assertFalse(demoted.contains(keep));
    }

    @Test
    void anEmptyCatalogIsNotAnError() {
        lenient().when(careerRequiredSkillRepository.findHighRowsWithEvidence()).thenReturn(List.of());
        assertEquals(0, demoter.demoteUnmeasurableCoreSkills());
    }

    @SuppressWarnings("unchecked")
    private Collection<UUID> demotedIds() {
        ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(careerRequiredSkillRepository).demoteToAvg(captor.capture());
        return captor.getValue();
    }

    /** One row shaped like {@code findHighRowsWithEvidence} returns it. */
    private static Object[] highRow(UUID rowId, String skillName, long postings,
                                    long nodeCount, long childCount) {
        return new Object[]{rowId, UUID.randomUUID(), skillName, postings, nodeCount, childCount};
    }
}
