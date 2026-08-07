package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.StudentSkill;
import com.inteliroadmap.backend.domain.entity.StudentSkillEvidence;
import com.inteliroadmap.backend.domain.enums.EvidenceStatus;
import com.inteliroadmap.backend.domain.enums.EvidenceType;
import com.inteliroadmap.backend.repositories.SkillRepository;
import com.inteliroadmap.backend.repositories.StudentRepository;
import com.inteliroadmap.backend.repositories.StudentSkillEvidenceRepository;
import com.inteliroadmap.backend.repositories.StudentSkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers {@code promoteFromEvidence}, the path that lets an analysed repository
 * reach the student's level.
 *
 * <p>The older {@code promote} maps evidence onto a skill through the nodes just
 * completed, so a student who connects GitHub before working through the roadmap
 * got nothing: no {@code verified_by}, a verified share of 0.00, and
 * {@code SeniorityCalculator.VERIFIED_FLOOR} holding them at JUNIOR forever.
 * These tests state the conditions under which that is fixed.
 */
@ExtendWith(MockitoExtension.class)
class SkillProficiencyPromoterTest {

    @Mock
    private StudentSkillEvidenceRepository evidenceRepository;
    @Mock
    private StudentSkillRepository studentSkillRepository;
    @Mock
    private SkillRepository skillRepository;
    @Mock
    private StudentRepository studentRepository;

    private SkillProficiencyPromoter promoter;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // A real canonicaliser over the mocked repository, not a mock of it. The identity
        // function is exactly what these tests depend on, so stubbing it would let a
        // regression in name matching pass unnoticed here.
        promoter = new SkillProficiencyPromoter(evidenceRepository, studentSkillRepository,
                skillRepository, studentRepository, new SkillNameCanonicalizer(skillRepository));
    }

    /** The whole point: no node has to be complete for a repository to count. */
    @Test
    void aRepositoryRaisesProficiencyWithoutAnyNodeBeingCompleted() {
        Skill java = skill("Java");
        StudentSkill existing = studentSkill(java, null, null);
        stubEvidence(evidence("Java", "0.88", EvidenceType.GITHUB_PROJECT));
        stubCatalog(java);
        when(studentSkillRepository.findByStudent_UserId(userId)).thenReturn(List.of(existing));

        int raised = promoter.promoteFromEvidence(userId, List.of(UUID.randomUUID()));

        assertEquals(1, raised);
        assertEquals((short) 4, existing.getProficiency(), "0.88 is at or above PROFESSIONAL_AT");
        assertEquals("GITHUB", existing.getVerifiedBy());
        assertFalse(existing.getSelfDeclared());
    }

    /**
     * A repository can prove a skill the student never thought to declare.
     * Skipping it would understate them, and the old promote() did exactly that
     * by iterating only over rows that already existed.
     */
    @Test
    void aSkillTheStudentNeverDeclaredGetsARowCreated() {
        Skill docker = skill("Docker");
        stubEvidence(evidence("Docker", "0.75", EvidenceType.GITHUB_PROJECT));
        stubCatalog(docker);
        when(studentSkillRepository.findByStudent_UserId(userId)).thenReturn(List.of());
        when(studentRepository.findById(userId))
                .thenReturn(Optional.of(Student.builder().userId(userId).build()));

        int raised = promoter.promoteFromEvidence(userId, List.of(UUID.randomUUID()));

        assertEquals(1, raised);
        StudentSkill created = captureSaved().get(0);
        assertEquals("Docker", created.getSkill().getSkillName());
        assertEquals((short) 3, created.getProficiency(), "0.75 is APPLIED, not PROFESSIONAL");
        assertEquals("GITHUB", created.getVerifiedBy());
    }

    /** Re-importing the same repository must not move anything a second time. */
    @Test
    void promotingTwiceFromTheSameEvidenceChangesNothingTheSecondTime() {
        Skill java = skill("Java");
        StudentSkill row = studentSkill(java, null, null);
        stubEvidence(evidence("Java", "0.88", EvidenceType.GITHUB_PROJECT));
        stubCatalog(java);
        when(studentSkillRepository.findByStudent_UserId(userId)).thenReturn(List.of(row));

        assertEquals(1, promoter.promoteFromEvidence(userId, List.of(UUID.randomUUID())));
        assertEquals(0, promoter.promoteFromEvidence(userId, List.of(UUID.randomUUID())),
                "nothing left to raise, so nothing is written");
    }

    /** A repo read at APPLIED must not knock a student down from a PROFESSIONAL they earned. */
    @Test
    void promotionOnlyEverRaises() {
        Skill java = skill("Java");
        StudentSkill row = studentSkill(java, (short) 4, "TRANSCRIPT");
        stubEvidence(evidence("Java", "0.72", EvidenceType.GITHUB_PROJECT));
        stubCatalog(java);
        when(studentSkillRepository.findByStudent_UserId(userId)).thenReturn(List.of(row));

        promoter.promoteFromEvidence(userId, List.of(UUID.randomUUID()));

        assertEquals((short) 4, row.getProficiency());
        assertEquals("TRANSCRIPT", row.getVerifiedBy(), "an existing verifier is never overwritten");
    }

    /**
     * MANUAL evidence is the student talking about themselves. It may raise the
     * proficiency but must leave verified_by null — it is the absence of a
     * verifier that keeps the JUNIOR ceiling doing its job.
     */
    @Test
    void selfReportedEvidenceNeverSetsAVerifier() {
        Skill java = skill("Java");
        StudentSkill row = studentSkill(java, null, null);
        stubEvidence(evidence("Java", "0.90", EvidenceType.MANUAL));
        stubCatalog(java);
        when(studentSkillRepository.findByStudent_UserId(userId)).thenReturn(List.of(row));

        promoter.promoteFromEvidence(userId, List.of(UUID.randomUUID()));

        assertNull(row.getVerifiedBy());
    }

    /** Two reads of the same skill settle on the stronger one, not on load order. */
    @Test
    void theStrongestEvidencePerSkillWins() {
        Skill java = skill("Java");
        StudentSkill row = studentSkill(java, null, null);
        stubEvidence(evidence("Java", "0.60", EvidenceType.GITHUB_PROJECT),
                evidence("Java", "0.88", EvidenceType.GITHUB_PROJECT));
        stubCatalog(java);
        when(studentSkillRepository.findByStudent_UserId(userId)).thenReturn(List.of(row));

        promoter.promoteFromEvidence(userId, List.of(UUID.randomUUID(), UUID.randomUUID()));

        assertEquals((short) 4, row.getProficiency());
    }

    /** A name with no catalog row is a hallucination; it must not mint a skill. */
    @Test
    void anEvidenceNameWithNoCatalogRowIsDiscarded() {
        stubEvidence(evidence("Blazingly Fast Rust Wizardry", "0.95", EvidenceType.GITHUB_PROJECT));
        when(skillRepository.findOneBySkillNameIgnoreCase(any())).thenReturn(null);

        assertEquals(0, promoter.promoteFromEvidence(userId, List.of(UUID.randomUUID())));
        verify(studentSkillRepository, never()).saveAll(anyList());
    }

    @Test
    void noEvidenceIsANoOp() {
        assertEquals(0, promoter.promoteFromEvidence(userId, List.of()));
        assertEquals(0, promoter.promoteFromEvidence(userId, null));
        verify(evidenceRepository, never()).findAllById(any());
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    /**
     * The Spring Boot case. The Backend tree has no node called `Spring Boot` — only
     * `Spring (Spring Boot)`, linked to a different skill — so acceptance, which used
     * to happen only for evidence attached to a node the recommendation engine had
     * just completed, could never reach it. The row raised the student to
     * PROFESSIONAL, was stamped verified by GitHub, and displayed as PENDING for
     * good.
     */
    @Test
    void evidenceIsAcceptedEvenWhenTheRoadmapHasNoNodeForTheSkill() {
        Skill springBoot = skill("Spring Boot");
        StudentSkillEvidence pending = evidence("Spring Boot", "0.85", EvidenceType.GITHUB_PROJECT);
        pending.setStatus(EvidenceStatus.PENDING);
        StudentSkill existing = studentSkill(springBoot, null, null);
        stubEvidence(pending);
        stubCatalog(springBoot);
        when(studentSkillRepository.findByStudent_UserId(userId)).thenReturn(List.of(existing));

        promoter.promoteFromEvidence(userId, List.of(UUID.randomUUID()));

        assertEquals(EvidenceStatus.ACCEPTED, pending.getStatus());
        assertEquals(existing.getStudentSkillId(), pending.getStudentSkillId(),
                "the accepted row should point at the skill it raised");
    }

    /**
     * Losing to stronger evidence is not a reason to stay open. Leaving the loser
     * PENDING would rebuild the same stuck state one rung down.
     */
    @Test
    void evidenceThatLostToAStrongerRowIsStillSettled() {
        Skill java = skill("Java");
        StudentSkillEvidence weak = evidence("Java", "0.60", EvidenceType.MANUAL);
        StudentSkillEvidence strong = evidence("Java", "0.90", EvidenceType.GITHUB_PROJECT);
        weak.setStatus(EvidenceStatus.PENDING);
        strong.setStatus(EvidenceStatus.PENDING);
        stubEvidence(weak, strong);
        stubCatalog(java);
        when(studentSkillRepository.findByStudent_UserId(userId))
                .thenReturn(List.of(studentSkill(java, null, null)));

        promoter.promoteFromEvidence(userId, List.of(UUID.randomUUID(), UUID.randomUUID()));

        assertEquals(EvidenceStatus.ACCEPTED, strong.getStatus());
        assertEquals(EvidenceStatus.ACCEPTED, weak.getStatus());
    }

    /** A name the catalog does not know really has not been processed — leave it alone. */
    @Test
    void evidenceThatResolvesToNoCatalogSkillStaysPending() {
        Skill java = skill("Java");
        StudentSkillEvidence known = evidence("Java", "0.90", EvidenceType.GITHUB_PROJECT);
        StudentSkillEvidence unknown = evidence("Frobnicator", "0.90", EvidenceType.GITHUB_PROJECT);
        known.setStatus(EvidenceStatus.PENDING);
        unknown.setStatus(EvidenceStatus.PENDING);
        stubEvidence(known, unknown);
        stubCatalog(java);
        lenient().when(skillRepository.findOneBySkillNameIgnoreCase("Frobnicator")).thenReturn(null);
        when(studentSkillRepository.findByStudent_UserId(userId))
                .thenReturn(List.of(studentSkill(java, null, null)));

        promoter.promoteFromEvidence(userId, List.of(UUID.randomUUID(), UUID.randomUUID()));

        assertEquals(EvidenceStatus.ACCEPTED, known.getStatus());
        assertEquals(EvidenceStatus.PENDING, unknown.getStatus());
    }

    private void stubEvidence(StudentSkillEvidence... rows) {
        when(evidenceRepository.findAllById(any())).thenReturn(List.of(rows));
    }

    private void stubCatalog(Skill... skills) {
        for (Skill skill : skills) {
            lenient().when(skillRepository.findOneBySkillNameIgnoreCase(skill.getSkillName()))
                    .thenReturn(skill);
            lenient().when(skillRepository.findById(skill.getSkillId()))
                    .thenReturn(Optional.of(skill));
        }
    }

    private List<StudentSkill> captureSaved() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StudentSkill>> captor = ArgumentCaptor.forClass(List.class);
        verify(studentSkillRepository).saveAll(captor.capture());
        return new ArrayList<>(captor.getValue());
    }

    // ---- revokeVerification: what a withdrawal actually costs -------------------

    /**
     * The reason the method exists. Deleting evidence rows is invisible on its own —
     * the verifier lives on student_skills, and that copy is what caps the level.
     */
    @Test
    void withdrawingTheLastBackingClearsTheVerifier() {
        StudentSkill row = studentSkill(skill("Java"), (short) 3, "GITHUB");
        row.setSelfDeclared(false);
        when(evidenceRepository.findByUserIdAndStatusIn(userId, List.of(EvidenceStatus.ACCEPTED)))
                .thenReturn(List.of());
        when(studentSkillRepository.findByStudent_UserId(userId)).thenReturn(List.of(row));

        assertEquals(1, promoter.revokeVerification(userId, List.of("Java")));

        assertNull(row.getVerifiedBy());
        assertTrue(row.getSelfDeclared());
    }

    /** The student did build the thing. Only the outside check is taken back. */
    @Test
    void revokingTheVerifierLeavesProficiencyAlone() {
        StudentSkill row = studentSkill(skill("Java"), (short) 4, "GITHUB");
        when(evidenceRepository.findByUserIdAndStatusIn(userId, List.of(EvidenceStatus.ACCEPTED)))
                .thenReturn(List.of());
        when(studentSkillRepository.findByStudent_UserId(userId)).thenReturn(List.of(row));

        promoter.revokeVerification(userId, List.of("Java"));

        assertEquals((short) 4, row.getProficiency());
    }

    /** Two repositories proved Java; deleting one of them proves nothing about the other. */
    @Test
    void aSkillStillBackedElsewhereKeepsItsVerifier() {
        StudentSkill row = studentSkill(skill("Java"), (short) 3, "GITHUB");
        StudentSkillEvidence surviving = evidence("java", "0.80", EvidenceType.GITHUB_PROJECT);
        when(evidenceRepository.findByUserIdAndStatusIn(userId, List.of(EvidenceStatus.ACCEPTED)))
                .thenReturn(List.of(surviving));

        assertEquals(0, promoter.revokeVerification(userId, List.of("Java")));

        assertEquals("GITHUB", row.getVerifiedBy());
        verify(studentSkillRepository, never()).saveAll(anyList());
    }

    /**
     * A leftover self-report is not a verifier. If it counted as surviving backing, the
     * verified share would never fall — every skill the student ticked at onboarding has
     * a MANUAL row.
     */
    @Test
    void aSurvivingSelfReportDoesNotCountAsBacking() {
        StudentSkill row = studentSkill(skill("Java"), (short) 3, "GITHUB");
        when(evidenceRepository.findByUserIdAndStatusIn(userId, List.of(EvidenceStatus.ACCEPTED)))
                .thenReturn(List.of(evidence("Java", "0.60", EvidenceType.MANUAL)));
        when(studentSkillRepository.findByStudent_UserId(userId)).thenReturn(List.of(row));

        assertEquals(1, promoter.revokeVerification(userId, List.of("Java")));

        assertNull(row.getVerifiedBy());
    }

    /** Names come off evidence rows, which do not always match the catalog's casing. */
    @Test
    void skillNamesAreMatchedCaseInsensitively() {
        StudentSkill row = studentSkill(skill("Spring Boot"), (short) 3, "GITHUB");
        when(evidenceRepository.findByUserIdAndStatusIn(userId, List.of(EvidenceStatus.ACCEPTED)))
                .thenReturn(List.of());
        when(studentSkillRepository.findByStudent_UserId(userId)).thenReturn(List.of(row));

        assertEquals(1, promoter.revokeVerification(userId, List.of("  spring boot ")));

        assertNull(row.getVerifiedBy());
    }

    /** Nothing withdrawn, nothing touched — and no query fired to find that out. */
    @Test
    void anEmptyWithdrawalIsANoOp() {
        assertEquals(0, promoter.revokeVerification(userId, List.of()));

        verify(studentSkillRepository, never()).findByStudent_UserId(any());
        verify(studentSkillRepository, never()).saveAll(anyList());
    }

    private StudentSkillEvidence evidence(String skillName, String confidence, EvidenceType type) {
        return StudentSkillEvidence.builder()
                .evidenceId(UUID.randomUUID())
                .userId(userId)
                .skillName(skillName)
                .sourceType(type)
                .confidence(new BigDecimal(confidence))
                .build();
    }

    private Skill skill(String name) {
        Skill skill = new Skill();
        skill.setSkillId(UUID.randomUUID());
        skill.setSkillName(name);
        return skill;
    }

    private StudentSkill studentSkill(Skill skill, Short proficiency, String verifiedBy) {
        return StudentSkill.builder()
                .student(Student.builder().userId(userId).build())
                .skill(skill)
                .proficiency(proficiency)
                .verifiedBy(verifiedBy)
                .build();
    }
}
