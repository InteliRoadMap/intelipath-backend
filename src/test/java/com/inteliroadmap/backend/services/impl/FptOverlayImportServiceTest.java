package com.inteliroadmap.backend.services.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inteliroadmap.backend.domain.entity.FptCurriculum;
import com.inteliroadmap.backend.repositories.FptCurriculumRepository;
import com.inteliroadmap.backend.repositories.FptCurriculumSubjectRepository;
import com.inteliroadmap.backend.repositories.FptSubjectCloRepository;
import com.inteliroadmap.backend.repositories.FptSubjectRepository;
import com.inteliroadmap.backend.repositories.FptSubjectResourceRepository;
import com.inteliroadmap.backend.repositories.FptSubjectSkillRepository;
import com.inteliroadmap.backend.repositories.SkillRepository;
import com.inteliroadmap.backend.services.FptOverlayImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The importer rebuilds a subject by deleting its skills, CLOs and resources before
 * re-inserting. On 2026-07-22 a live sync returned 47 subjects with no detail at all and
 * that delete ran anyway, wiping every CLO and every mirrored-file row in the database and
 * orphaning files that were still sitting in storage. These tests pin the rule that stops
 * it: an overlay only gets to replace a facet it actually carries.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FptOverlayImportServiceTest {

    @Mock private SkillRepository skillRepository;
    @Mock private FptSubjectRepository fptSubjectRepository;
    @Mock private FptSubjectSkillRepository fptSubjectSkillRepository;
    @Mock private FptSubjectResourceRepository fptSubjectResourceRepository;
    @Mock private FptSubjectCloRepository fptSubjectCloRepository;
    @Mock private FptCurriculumRepository fptCurriculumRepository;
    @Mock private FptCurriculumSubjectRepository fptCurriculumSubjectRepository;

    private FptOverlayImportService service;

    private static final FptOverlayImportService.CurriculumRef REF =
            new FptOverlayImportService.CurriculumRef("BIT_SE_K19D_K20A", "2941", false);

    @BeforeEach
    void setUp() {
        service = new FptOverlayImportServiceImpl(
                skillRepository,
                fptSubjectRepository,
                fptSubjectSkillRepository,
                fptSubjectResourceRepository,
                fptSubjectCloRepository,
                fptCurriculumRepository,
                fptCurriculumSubjectRepository);

        when(fptCurriculumRepository.findByCode(anyString())).thenReturn(Optional.empty());
        when(fptCurriculumRepository.save(any())).thenAnswer(inv -> {
            FptCurriculum c = inv.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            return c;
        });
    }

    private static JsonNode overlay(String json) {
        try {
            return new ObjectMapper().readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void emptyOverlayLeavesStoredDetailAlone() {
        // Exactly what the failed scrape sent: subject shells, nothing else.
        JsonNode root = overlay("""
                {"subjects":[
                  {"code":"DBI202","name":"DBI202","description":"Databases"},
                  {"code":"PRJ301","name":"PRJ301","sessions":[],"materials":[],"skills":[]}
                ]}
                """);

        FptOverlayImportService.ImportSummary summary = service.importOverlay(root, REF);

        verify(fptSubjectCloRepository, never()).deleteBySubjectCode(anyString());
        verify(fptSubjectResourceRepository, never()).deleteBySubjectCode(anyString());
        verify(fptSubjectSkillRepository, never()).deleteBySubjectCode(anyString());

        // The subjects themselves are still upserted — only the detail is protected.
        assertEquals(2, summary.subjects());
        assertEquals(0, summary.clos());
        assertEquals(0, summary.resources());
        assertEquals(2, summary.preservedSubjects());
    }

    @Test
    void overlayCarryingDetailStillReplacesIt() {
        JsonNode root = overlay("""
                {"subjects":[
                  {"code":"DBI202","name":"Database Systems","credits":3,
                   "skills":["SQL"],
                   "clos":[{"code":"CLO1","outcome":"Design a schema"}],
                   "sessions":[{"session":"1","topic":"Relational model","download":"http://x/1.zip"}],
                   "materials":[{"description":"Textbook","url":"http://x/book"}]}
                ]}
                """);
        when(skillRepository.findBySkillName("SQL")).thenReturn(null);

        FptOverlayImportService.ImportSummary summary = service.importOverlay(root, REF);

        verify(fptSubjectCloRepository).deleteBySubjectCode("DBI202");
        verify(fptSubjectResourceRepository).deleteBySubjectCode("DBI202");
        verify(fptSubjectSkillRepository).deleteBySubjectCode("DBI202");

        assertEquals(1, summary.clos());
        assertEquals(2, summary.resources());   // one session + one material
        assertEquals(1, summary.skillLinks());
        assertEquals(0, summary.preservedSubjects());
    }

    @Test
    void facetsAreProtectedIndependently() {
        // A real partial scrape: outcomes came back, the file listing did not. The CLOs
        // must land without the missing sessions taking the stored files down with them.
        JsonNode root = overlay("""
                {"subjects":[
                  {"code":"DBI202","name":"Database Systems",
                   "clos":[{"code":"CLO1","outcome":"Design a schema"}]}
                ]}
                """);

        FptOverlayImportService.ImportSummary summary = service.importOverlay(root, REF);

        verify(fptSubjectCloRepository).deleteBySubjectCode("DBI202");
        verify(fptSubjectResourceRepository, never()).deleteBySubjectCode(anyString());
        verify(fptSubjectSkillRepository, never()).deleteBySubjectCode(anyString());

        assertEquals(1, summary.clos());
        assertEquals(0, summary.preservedSubjects());
        assertTrue(summary.resources() == 0);
    }
}
