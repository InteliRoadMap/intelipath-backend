package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.entity.SkillNode;
import com.inteliroadmap.backend.domain.entity.StudentSkill;
import com.inteliroadmap.backend.domain.entity.StudentSkillEvidence;
import com.inteliroadmap.backend.domain.enums.EvidenceStatus;
import com.inteliroadmap.backend.domain.enums.EvidenceType;
import com.inteliroadmap.backend.repositories.SkillRepository;
import com.inteliroadmap.backend.repositories.StudentRepository;
import com.inteliroadmap.backend.repositories.StudentSkillEvidenceRepository;
import com.inteliroadmap.backend.repositories.StudentSkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Writes proficiency and the verifying source onto student_skills after a
 * recommendation is accepted.
 *
 * <p>Why this exists: {@code student_skills} rows created by the roadmap's own
 * completion sync carry no proficiency and no {@code verified_by}, and
 * {@link SeniorityCalculator} ignores a row with no proficiency. Without this
 * step the verified ratio stays at 0.00 for every student in the system no
 * matter how many repositories or transcripts they connect, so
 * {@link SeniorityCalculator#VERIFIED_FLOOR} always trips and the level is
 * permanently capped at JUNIOR. Objective evidence had no way to reach the
 * level at all — this is what connects it.
 *
 * <p>Additive by design: it runs <em>after</em> the existing sync and only ever
 * raises a row. It never lowers a proficiency and never clears a
 * {@code verified_by} that a stronger source already set, so re-running it (or
 * re-importing the same repository) is idempotent.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SkillProficiencyPromoter {

    /** Confidence at or above which the evidence reads as PROFESSIONAL, then APPLIED. */
    private static final BigDecimal PROFESSIONAL_AT = new BigDecimal("0.85");
    private static final BigDecimal APPLIED_AT = new BigDecimal("0.70");

    /** Below {@link #APPLIED_AT} the evidence only supports PRACTICED. */
    private static final short PROFESSIONAL = 4;
    private static final short APPLIED = 3;
    private static final short PRACTICED = 2;

    private final StudentSkillEvidenceRepository evidenceRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final SkillRepository skillRepository;
    private final StudentRepository studentRepository;
    private final SkillNameCanonicalizer skillNameCanonicalizer;

    /**
     * Promotes from evidence alone, without waiting for a node to be completed.
     *
     * <p>Sits beside {@link #promote} rather than replacing it. That method maps an
     * evidence row onto a skill <em>through the nodes just completed</em>, so
     * importing a React repository while no React node is finished leaves
     * {@code skillIdByName} empty and the method returns 0 without a word. In
     * practice that is the ordinary case — a student connects GitHub precisely
     * because they have not worked through the roadmap yet — so the objective
     * evidence the whole verified-ratio mechanism depends on never arrived.
     *
     * <p>Two differences beyond the trigger:
     * <ul>
     *   <li>the skill is resolved from {@code evidence.skillName} against the
     *       catalog, which is where {@code SkillEvidenceService} already
     *       canonicalised it, so no node has to exist;
     *   <li>a missing {@code student_skills} row is created rather than skipped. A
     *       repository can prove a skill the student never thought to declare, and
     *       dropping it would understate them.
     * </ul>
     *
     * <p>Still only ever raises, so re-importing the same repository is a no-op.
     *
     * @return how many rows were raised or created
     */
    public int promoteFromEvidence(UUID userId, Collection<UUID> evidenceIds) {
        if (userId == null || evidenceIds == null || evidenceIds.isEmpty()) {
            return 0;
        }

        // Strongest evidence wins per skill, same rule as promote(): a 0.88 repo read
        // and a 0.60 self-report on Java should settle on PROFESSIONAL.
        Map<UUID, StudentSkillEvidence> strongestBySkillId = new HashMap<>();
        // Every row that resolved to a catalog skill, not only the winner — see
        // acceptResolvedEvidence for why the losers matter too.
        Map<StudentSkillEvidence, UUID> skillIdByEvidence = new LinkedHashMap<>();
        for (StudentSkillEvidence evidence : evidenceRepository.findAllById(evidenceIds)) {
            if (evidence.getConfidence() == null || evidence.getSkillName() == null) {
                continue;
            }
            // Through the canonicaliser, not a string compare. Evidence stores the skill
            // as free text, so a row written before the catalog merge names "Fast API"
            // while the catalog now holds only "FastAPI" - an exact lookup would drop
            // the student's own verified evidence on the floor.
            Skill skill = skillNameCanonicalizer.resolve(evidence.getSkillName().trim());
            if (skill == null || skill.getSkillId() == null) {
                continue;
            }
            skillIdByEvidence.put(evidence, skill.getSkillId());
            StudentSkillEvidence current = strongestBySkillId.get(skill.getSkillId());
            if (current == null || evidence.getConfidence().compareTo(current.getConfidence()) > 0) {
                strongestBySkillId.put(skill.getSkillId(), evidence);
            }
        }
        if (strongestBySkillId.isEmpty()) {
            return 0;
        }

        List<StudentSkill> toSave = new ArrayList<>();
        Map<UUID, StudentSkill> existingBySkillId = new HashMap<>();
        for (StudentSkill row : studentSkillRepository.findByStudent_UserId(userId)) {
            if (row.getSkill() != null && row.getSkill().getSkillId() != null) {
                existingBySkillId.put(row.getSkill().getSkillId(), row);
            }
        }

        int created = 0;
        for (Map.Entry<UUID, StudentSkillEvidence> entry : strongestBySkillId.entrySet()) {
            StudentSkillEvidence evidence = entry.getValue();
            StudentSkill row = existingBySkillId.get(entry.getKey());
            if (row == null) {
                Skill skill = skillRepository.findById(entry.getKey()).orElse(null);
                if (skill == null) {
                    continue;
                }
                row = StudentSkill.builder()
                        .student(studentRepository.findById(userId).orElse(null))
                        .skill(skill)
                        .build();
                if (row.getStudent() == null) {
                    continue;
                }
                created++;
            }
            if (applyTo(row, evidence)) {
                toSave.add(row);
            }
        }

        if (!toSave.isEmpty()) {
            studentSkillRepository.saveAll(toSave);
            log.info("SkillProficiencyPromoter: raised {} skill row(s) for user {} from evidence "
                            + "({} newly created).",
                    toSave.size(), userId, created);
        }
        acceptResolvedEvidence(userId, skillIdByEvidence);
        return toSave.size();
    }

    /**
     * Closes off the evidence this promotion consumed.
     *
     * <p>Acceptance used to happen in one place only: the recommendation engine, and
     * only for evidence attached to a roadmap node it had just completed. Anything
     * the roadmap did not model could therefore never leave PENDING. A repository
     * read that named Spring Boot raised the student's proficiency to PROFESSIONAL
     * and marked it verified by GitHub, then sat as PENDING for good, because the
     * Backend tree has no node called Spring Boot — only `Spring (Spring Boot)`,
     * linked to a different skill. The evidence was believed and acted upon and
     * still displayed as unreviewed.
     *
     * <p>So PENDING now means what it says: not yet processed. Once a row has been
     * resolved to a catalog skill and weighed, it is settled — whether or not the
     * roadmap happens to contain a node for it, and whether or not it won its skill.
     * A row that lost to stronger evidence was still read and still counted toward
     * nothing further; leaving it open would recreate the same stuck state one rung
     * down. Rows that resolved to no catalog skill are left alone, because those
     * genuinely have not been processed.
     */
    private void acceptResolvedEvidence(UUID userId, Map<StudentSkillEvidence, UUID> skillIdByEvidence) {
        if (skillIdByEvidence.isEmpty()) {
            return;
        }
        Map<UUID, UUID> studentSkillIdBySkillId = new HashMap<>();
        for (StudentSkill row : studentSkillRepository.findByStudent_UserId(userId)) {
            if (row.getSkill() != null && row.getSkill().getSkillId() != null) {
                studentSkillIdBySkillId.put(row.getSkill().getSkillId(), row.getStudentSkillId());
            }
        }

        List<StudentSkillEvidence> settled = new ArrayList<>();
        for (Map.Entry<StudentSkillEvidence, UUID> entry : skillIdByEvidence.entrySet()) {
            StudentSkillEvidence evidence = entry.getKey();
            boolean changed = false;
            if (evidence.getStatus() == EvidenceStatus.PENDING) {
                evidence.setStatus(EvidenceStatus.ACCEPTED);
                changed = true;
            }
            if (evidence.getStudentSkillId() == null) {
                UUID studentSkillId = studentSkillIdBySkillId.get(entry.getValue());
                if (studentSkillId != null) {
                    evidence.setStudentSkillId(studentSkillId);
                    changed = true;
                }
            }
            if (changed) {
                settled.add(evidence);
            }
        }

        if (!settled.isEmpty()) {
            evidenceRepository.saveAll(settled);
            log.info("SkillProficiencyPromoter: settled {} evidence row(s) for user {}.", settled.size(), userId);
        }
    }

    /**
     * Promotes every student_skills row backed by one of the given evidence rows.
     *
     * @param userId         the student
     * @param completedNodes nodes just marked complete, used to map evidence rows
     *                       that carry a node id onto a catalog skill
     * @param evidenceIds    the evidence backing the accepted recommendation
     * @return how many rows were raised
     */
    public int promote(UUID userId, List<SkillNode> completedNodes, Collection<UUID> evidenceIds) {
        if (evidenceIds == null || evidenceIds.isEmpty()) {
            return 0;
        }

        // node -> skill, and skill name -> skill id, so evidence recorded either way resolves.
        Map<UUID, UUID> skillIdByNodeId = new HashMap<>();
        Map<String, UUID> skillIdByName = new HashMap<>();
        for (SkillNode node : completedNodes) {
            if (node.getSkill() == null || node.getSkill().getSkillId() == null) {
                continue;
            }
            UUID skillId = node.getSkill().getSkillId();
            skillIdByNodeId.put(node.getNodeId(), skillId);
            if (node.getSkill().getSkillName() != null) {
                skillIdByName.put(normalize(node.getSkill().getSkillName()), skillId);
            }
        }

        // Strongest evidence wins per skill: a student who has both a 0.60 self-report
        // and a 0.88 repo read on Java should end up verified at PROFESSIONAL, not
        // stuck at whichever row happened to be loaded last.
        Map<UUID, StudentSkillEvidence> strongestBySkillId = new HashMap<>();
        for (StudentSkillEvidence evidence : evidenceRepository.findAllById(evidenceIds)) {
            UUID skillId = resolveSkillId(evidence, skillIdByNodeId, skillIdByName);
            if (skillId == null || evidence.getConfidence() == null) {
                continue;
            }
            StudentSkillEvidence current = strongestBySkillId.get(skillId);
            if (current == null || evidence.getConfidence().compareTo(current.getConfidence()) > 0) {
                strongestBySkillId.put(skillId, evidence);
            }
        }
        if (strongestBySkillId.isEmpty()) {
            return 0;
        }

        List<StudentSkill> raised = new ArrayList<>();
        for (StudentSkill row : studentSkillRepository.findByStudent_UserId(userId)) {
            if (row.getSkill() == null || row.getSkill().getSkillId() == null) {
                continue;
            }
            StudentSkillEvidence evidence = strongestBySkillId.get(row.getSkill().getSkillId());
            if (evidence == null) {
                continue;
            }
            if (applyTo(row, evidence)) {
                raised.add(row);
            }
        }

        if (!raised.isEmpty()) {
            studentSkillRepository.saveAll(raised);
            log.info("SkillProficiencyPromoter: raised {} skill row(s) for user {} from accepted evidence.",
                    raised.size(), userId);
        }
        return raised.size();
    }

    /** @return true when the row actually changed. */
    private boolean applyTo(StudentSkill row, StudentSkillEvidence evidence) {
        short level = proficiencyOf(evidence.getConfidence());
        String verifier = verifierOf(evidence.getSourceType());
        boolean changed = false;

        // Only ever upwards. A repo read that lands at APPLIED must not knock a
        // student down from a PROFESSIONAL they earned elsewhere.
        if (row.getProficiency() == null || row.getProficiency() < level) {
            row.setProficiency(level);
            changed = true;
        }
        // A self-declared source leaves verified_by alone: it is exactly the
        // absence of a verifier that keeps the JUNIOR ceiling in place.
        if (verifier != null && (row.getVerifiedBy() == null || row.getVerifiedBy().isBlank())) {
            row.setVerifiedBy(verifier);
            row.setSelfDeclared(false);
            changed = true;
        }
        return changed;
    }

    private short proficiencyOf(BigDecimal confidence) {
        if (confidence.compareTo(PROFESSIONAL_AT) >= 0) return PROFESSIONAL;
        if (confidence.compareTo(APPLIED_AT) >= 0) return APPLIED;
        return PRACTICED;
    }

    /**
     * The label stored in {@code verified_by}, or null when the source is the
     * student's own word. CHAT_FILE and MANUAL are self-supplied: an uploaded
     * file or a ticked checkbox proves nothing on its own.
     */
    private String verifierOf(EvidenceType sourceType) {
        if (sourceType == EvidenceType.GITHUB_PROJECT) return "GITHUB";
        if (sourceType == EvidenceType.TRANSCRIPT) return "TRANSCRIPT";
        return null;
    }

    /**
     * Clears {@code verified_by} on skills that no longer have a verifying source.
     *
     * <p>The inverse of {@link #applyTo}, and the reason a withdrawal is felt at all.
     * Deleting evidence rows on its own changes nothing the student can see: this class
     * copies the verifier onto {@code student_skills} the moment evidence lands, and that
     * copy is what {@link SeniorityCalculator#VERIFIED_FLOOR} reads. Withdraw the evidence
     * without this step and the profile still claims GitHub vouched for the skill, with
     * nothing left in the database that ever did.
     *
     * <p><b>Proficiency is left alone.</b> Only the verifier is revoked. A student who
     * built the project did learn the skill, and this class has never lowered a
     * proficiency — a repository read at APPLIED must not be able to knock down a
     * PROFESSIONAL earned elsewhere, in either direction of the operation. What the
     * withdrawal takes back is the *claim that someone else checked*, which is precisely
     * what was borrowed.
     *
     * <p>Surviving evidence wins. A skill proven by two repositories keeps its verifier
     * when one of them is withdrawn — the check is made against what is still ACCEPTED,
     * not against what was just deleted.
     *
     * @param skillNames skills whose backing was withdrawn, as recorded on the evidence rows
     * @return how many rows lost their verifier
     */
    public int revokeVerification(UUID userId, Collection<String> skillNames) {
        if (userId == null || skillNames == null || skillNames.isEmpty()) {
            return 0;
        }
        Map<String, Boolean> wanted = new HashMap<>();
        for (String name : skillNames) {
            if (name != null && !name.isBlank()) {
                wanted.put(normalize(name), Boolean.TRUE);
            }
        }
        if (wanted.isEmpty()) {
            return 0;
        }

        // Read after the deletion, so a skill still backed by another repository or a
        // transcript drops out of `wanted` before anything is written.
        for (StudentSkillEvidence surviving : evidenceRepository
                .findByUserIdAndStatusIn(userId, List.of(EvidenceStatus.ACCEPTED))) {
            if (surviving.getSkillName() != null && verifierOf(surviving.getSourceType()) != null) {
                wanted.remove(normalize(surviving.getSkillName()));
            }
        }
        if (wanted.isEmpty()) {
            log.info("SkillProficiencyPromoter: withdrawal for user {} revoked nothing — "
                    + "every affected skill is still backed by other evidence.", userId);
            return 0;
        }

        List<StudentSkill> revoked = new ArrayList<>();
        for (StudentSkill row : studentSkillRepository.findByStudent_UserId(userId)) {
            if (row.getSkill() == null || row.getSkill().getSkillName() == null) {
                continue;
            }
            if (!wanted.containsKey(normalize(row.getSkill().getSkillName()))) {
                continue;
            }
            if (row.getVerifiedBy() == null || row.getVerifiedBy().isBlank()) {
                continue;
            }
            row.setVerifiedBy(null);
            // Back to the student's own word, which is what the row now rests on.
            row.setSelfDeclared(true);
            revoked.add(row);
        }

        if (!revoked.isEmpty()) {
            studentSkillRepository.saveAll(revoked);
            log.info("SkillProficiencyPromoter: revoked the verifier on {} skill row(s) for user {} "
                    + "after evidence was withdrawn.", revoked.size(), userId);
        }
        return revoked.size();
    }

    private UUID resolveSkillId(StudentSkillEvidence evidence,
                                Map<UUID, UUID> skillIdByNodeId,
                                Map<String, UUID> skillIdByName) {
        if (evidence.getNodeId() != null) {
            UUID byNode = skillIdByNodeId.get(evidence.getNodeId());
            if (byNode != null) {
                return byNode;
            }
        }
        if (evidence.getSkillName() != null) {
            return skillIdByName.get(normalize(evidence.getSkillName()));
        }
        return null;
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
