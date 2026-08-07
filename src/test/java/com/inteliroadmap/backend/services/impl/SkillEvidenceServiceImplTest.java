package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.components.SkillNameCanonicalizer;
import com.inteliroadmap.backend.components.SkillProficiencyPromoter;
import com.inteliroadmap.backend.domain.dto.ai.SkillMatch;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.entity.StudentSkillEvidence;
import com.inteliroadmap.backend.domain.enums.EvidenceStatus;
import com.inteliroadmap.backend.domain.enums.EvidenceType;
import com.inteliroadmap.backend.repositories.CareerRequiredSkillRepository;
import com.inteliroadmap.backend.repositories.SkillRepository;
import com.inteliroadmap.backend.repositories.StudentSkillEvidenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the rule that decides whether an analysed repository is allowed to say
 * anything at all about a skill.
 *
 * <p>{@code recordEvidence} used to skip any skill that already had a PENDING or
 * ACCEPTED row, whatever its source. The skill-selection screen writes an
 * ACCEPTED {@code MANUAL} row for every skill a student ticks, so declaring
 * "Java" at onboarding permanently blocked a Java repository from ever
 * recording GitHub evidence — and the block landed hardest on the skills the
 * student cared enough to declare. In the live database this showed as five
 * MANUAL evidence rows and zero {@code student_skills} rows with a
 * {@code verified_by}.
 */
@ExtendWith(MockitoExtension.class)
class SkillEvidenceServiceImplTest {

    @Mock
    private CareerRequiredSkillRepository careerRequiredSkillRepository;
    @Mock
    private SkillRepository skillRepository;
    @Mock
    private StudentSkillEvidenceRepository evidenceRepository;
    @Mock
    private SkillProficiencyPromoter skillProficiencyPromoter;

    private SkillEvidenceServiceImpl service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Real canonicaliser over the mocked repository - see SkillProficiencyPromoterTest.
        service = new SkillEvidenceServiceImpl(careerRequiredSkillRepository, skillRepository,
                evidenceRepository, skillProficiencyPromoter,
                new SkillNameCanonicalizer(skillRepository));
        lenient().when(evidenceRepository.saveAll(anyList()))
                .thenAnswer(invocation -> {
                    List<StudentSkillEvidence> rows = invocation.getArgument(0);
                    rows.forEach(row -> {
                        if (row.getEvidenceId() == null) {
                            row.setEvidenceId(UUID.randomUUID());
                        }
                    });
                    return rows;
                });
    }

    /** The regression this fix exists for. */
    @Test
    void aDeclaredSkillDoesNotBlockGithubEvidenceForTheSameSkill() {
        stubCatalog("Java");
        stubExisting(selfDeclared("Java"));

        List<UUID> ids = service.recordEvidence(userId, List.of(new SkillMatch("Java", 0.88)),
                EvidenceType.GITHUB_PROJECT, null, "https://github.com/me/api");

        assertEquals(1, ids.size(), "the repository must be recorded, not skipped");
        StudentSkillEvidence recorded = capturedFresh().get(0);
        assertEquals(EvidenceType.GITHUB_PROJECT, recorded.getSourceType());
        assertEquals("https://github.com/me/api", recorded.getSourceUrl());
    }

    /** The self-report it replaced must be retired, not left standing beside it. */
    @Test
    void theSupersededSelfReportIsRejectedAndSaysWhy() {
        stubCatalog("Java");
        StudentSkillEvidence declared = selfDeclared("Java");
        stubExisting(declared);

        service.recordEvidence(userId, List.of(new SkillMatch("Java", 0.88)),
                EvidenceType.GITHUB_PROJECT, null, "https://github.com/me/api");

        assertEquals(EvidenceStatus.REJECTED, declared.getStatus());
        assertTrue(declared.getEvidenceText().contains("GITHUB_PROJECT"), declared.getEvidenceText());
        assertTrue(declared.getEvidenceText().contains("https://github.com/me/api"));
    }

    /**
     * A repository read outranks a self-report on kind, not on score. Gating on
     * confidence would let the flat 0.60 self-report block a 0.45 repository read,
     * which is the same bug wearing a different hat.
     */
    @Test
    void aLowConfidenceRepositoryStillBeatsAHigherConfidenceSelfReport() {
        stubCatalog("Java");
        StudentSkillEvidence declared = selfDeclared("Java");
        stubExisting(declared);

        List<UUID> ids = service.recordEvidence(userId, List.of(new SkillMatch("Java", 0.45)),
                EvidenceType.GITHUB_PROJECT, null, "https://github.com/me/api");

        assertEquals(1, ids.size());
        assertEquals(EvidenceStatus.REJECTED, declared.getStatus());
    }

    /** Two repositories proving the same skill add nothing the second time. */
    @Test
    void anotherObjectiveSourceForTheSameSkillIsNotDuplicated() {
        stubCatalog("Java");
        StudentSkillEvidence fromRepo = StudentSkillEvidence.builder()
                .evidenceId(UUID.randomUUID())
                .userId(userId)
                .skillName("Java")
                .sourceType(EvidenceType.GITHUB_PROJECT)
                .confidence(new BigDecimal("0.80"))
                .detectedBy("ai-service")
                .status(EvidenceStatus.ACCEPTED)
                .build();
        stubExisting(fromRepo);

        List<UUID> ids = service.recordEvidence(userId, List.of(new SkillMatch("Java", 0.88)),
                EvidenceType.GITHUB_PROJECT, null, "https://github.com/me/other");

        assertTrue(ids.isEmpty());
        assertEquals(EvidenceStatus.ACCEPTED, fromRepo.getStatus(), "an objective row is left alone");
    }

    /**
     * Re-importing the same repository must work, and used to be a silent no-op.
     *
     * <p>Measured on the running database: a student deleted every project from their
     * portfolio and eight ACCEPTED evidence rows stayed behind. Re-importing any of those
     * repositories then found its OWN earlier rows sitting in the duplicate guard as
     * accepted objective evidence, skipped every skill, recorded nothing and promoted
     * nothing. From the outside: click Import, wait for a model call, nothing happens,
     * no error.
     */
    @Test
    void reimportingTheSameRepositoryRecordsEvidenceAgain() {
        stubCatalog("Java");
        String repo = "https://github.com/me/api";
        StudentSkillEvidence firstImport = fromRepository("Java", repo);
        stubExisting(firstImport);
        when(evidenceRepository.findByUserIdAndSourceUrl(userId, repo)).thenReturn(List.of(firstImport));

        List<UUID> ids = service.recordEvidence(userId, List.of(new SkillMatch("Java", 0.88)),
                EvidenceType.GITHUB_PROJECT, null, repo);

        assertEquals(1, ids.size(), "a re-analysis replaces its own answer, it does not compete with it");
        verify(evidenceRepository).deleteAll(List.of(firstImport));
    }

    /** Matching is case-insensitive; GitHub URLs are not typed by hand every time. */
    @Test
    void reimportMatchesTheSourceUrlIgnoringCase() {
        stubCatalog("Java");
        StudentSkillEvidence firstImport = fromRepository("Java", "https://github.com/Me/API");
        stubExisting(firstImport);
        when(evidenceRepository.findByUserIdAndSourceUrl(userId, "https://github.com/me/api"))
                .thenReturn(List.of());

        List<UUID> ids = service.recordEvidence(userId, List.of(new SkillMatch("Java", 0.88)),
                EvidenceType.GITHUB_PROJECT, null, "https://github.com/me/api");

        assertEquals(1, ids.size());
    }

    /**
     * The narrowness of the fix, pinned. Withdrawal is for the source being re-read; a
     * different repository proving the same skill is still a competing claim and must
     * still be left alone.
     */
    @Test
    void withdrawalDoesNotReachEvidenceFromOtherRepositories() {
        stubCatalog("Java");
        StudentSkillEvidence otherRepo = fromRepository("Java", "https://github.com/me/other");
        stubExisting(otherRepo);
        when(evidenceRepository.findByUserIdAndSourceUrl(userId, "https://github.com/me/api"))
                .thenReturn(List.of());

        List<UUID> ids = service.recordEvidence(userId, List.of(new SkillMatch("Java", 0.88)),
                EvidenceType.GITHUB_PROJECT, null, "https://github.com/me/api");

        assertTrue(ids.isEmpty(), "another repository already stands for this skill");
        assertEquals(EvidenceStatus.ACCEPTED, otherRepo.getStatus());
        verify(evidenceRepository, never()).deleteAll(anyList());
    }

    /** A null URL identifies nothing, so it must never be used as a key to delete by. */
    @Test
    void aSourcelessImportWithdrawsNothing() {
        stubCatalog("Java");
        stubExisting();

        service.recordEvidence(userId, List.of(new SkillMatch("Java", 0.88)),
                EvidenceType.GITHUB_PROJECT, null, null);

        verify(evidenceRepository, never()).deleteAll(anyList());
    }

    /** One batch naming a skill twice must not supersede the row it just created. */
    @Test
    void aSkillNamedTwiceInOneBatchIsRecordedOnce() {
        stubCatalog("Java");
        stubExisting();

        List<UUID> ids = service.recordEvidence(userId,
                List.of(new SkillMatch("Java", 0.88), new SkillMatch("java", 0.70)),
                EvidenceType.GITHUB_PROJECT, null, "https://github.com/me/api");

        assertEquals(1, ids.size());
    }

    /** Anti-hallucination: a name with no catalog row is discarded, never minted. */
    @Test
    void aSkillWithNoCatalogRowIsDiscarded() {
        when(skillRepository.findOneBySkillNameIgnoreCase(any())).thenReturn(null);
        stubExisting();

        List<UUID> ids = service.recordEvidence(userId,
                List.of(new SkillMatch("Blazingly Fast Rust Wizardry", 0.95)),
                EvidenceType.GITHUB_PROJECT, null, "https://github.com/me/api");

        assertTrue(ids.isEmpty());
        verify(evidenceRepository, never()).saveAll(anyList());
    }

    @Test
    void evidenceBelowTheRecordableFloorIsIgnored() {
        stubCatalog("Java");
        stubExisting();

        List<UUID> ids = service.recordEvidence(userId, List.of(new SkillMatch("Java", 0.10)),
                EvidenceType.GITHUB_PROJECT, null, "https://github.com/me/api");

        assertTrue(ids.isEmpty());
    }

    @Test
    void noMatchesIsANoOp() {
        assertTrue(service.recordEvidence(userId, List.of(), EvidenceType.GITHUB_PROJECT, null, null).isEmpty());
        assertTrue(service.recordEvidence(userId, null, EvidenceType.GITHUB_PROJECT, null, null).isEmpty());
        verify(evidenceRepository, never()).saveAll(anyList());
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    private void stubCatalog(String... names) {
        for (String name : names) {
            Skill skill = new Skill();
            skill.setSkillId(UUID.randomUUID());
            skill.setSkillName(name);
            lenient().when(skillRepository.findOneBySkillNameIgnoreCase(name)).thenReturn(skill);
            lenient().when(skillRepository.findOneBySkillNameIgnoreCase(name.toLowerCase()))
                    .thenReturn(skill);
        }
    }

    private void stubExisting(StudentSkillEvidence... rows) {
        when(evidenceRepository.findByUserIdAndStatusIn(any(), any())).thenReturn(List.of(rows));
    }

    /** What the skill-selection screen writes: ACCEPTED, MANUAL, student-self-report. */
    private StudentSkillEvidence selfDeclared(String skillName) {
        return StudentSkillEvidence.builder()
                .evidenceId(UUID.randomUUID())
                .userId(userId)
                .skillName(skillName)
                .sourceType(EvidenceType.MANUAL)
                .confidence(new BigDecimal("0.60"))
                .detectedBy("student-self-report")
                .status(EvidenceStatus.ACCEPTED)
                .build();
    }

    /** An accepted row a repository read produced, attributed to that repository's URL. */
    private StudentSkillEvidence fromRepository(String skillName, String sourceUrl) {
        return StudentSkillEvidence.builder()
                .evidenceId(UUID.randomUUID())
                .userId(userId)
                .skillName(skillName)
                .sourceType(EvidenceType.GITHUB_PROJECT)
                .sourceUrl(sourceUrl)
                .confidence(new BigDecimal("0.80"))
                .detectedBy("ai-service")
                .status(EvidenceStatus.ACCEPTED)
                .build();
    }

    /** The rows written by the second saveAll — the newly recorded evidence. */
    private List<StudentSkillEvidence> capturedFresh() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StudentSkillEvidence>> captor = ArgumentCaptor.forClass(List.class);
        verify(evidenceRepository, org.mockito.Mockito.atLeastOnce()).saveAll(captor.capture());
        List<StudentSkillEvidence> last = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertFalse(last.isEmpty());
        return new ArrayList<>(last);
    }
}
