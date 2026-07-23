package com.inteliroadmap.backend.services.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.inteliroadmap.backend.domain.entity.FptCurriculum;
import com.inteliroadmap.backend.domain.entity.FptCurriculumSubject;
import com.inteliroadmap.backend.domain.entity.FptSubjectClo;
import com.inteliroadmap.backend.domain.entity.FptSubject;
import com.inteliroadmap.backend.domain.entity.FptSubjectResource;
import com.inteliroadmap.backend.domain.entity.FptSubjectSkill;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.enums.FptResourceKind;
import com.inteliroadmap.backend.repositories.FptCurriculumRepository;
import com.inteliroadmap.backend.repositories.FptCurriculumSubjectRepository;
import com.inteliroadmap.backend.repositories.FptSubjectCloRepository;
import com.inteliroadmap.backend.repositories.FptSubjectRepository;
import com.inteliroadmap.backend.repositories.FptSubjectResourceRepository;
import com.inteliroadmap.backend.repositories.FptSubjectSkillRepository;
import com.inteliroadmap.backend.repositories.SkillRepository;
import com.inteliroadmap.backend.services.FptOverlayImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class FptOverlayImportServiceImpl implements FptOverlayImportService {

    // First underscore-token shaped like K<digits><rest>, e.g. K21B or K19D.
    private static final Pattern COHORT_TOKEN = Pattern.compile("^K(\\d+)(.*)$");

    private final SkillRepository skillRepository;
    private final FptSubjectRepository fptSubjectRepository;
    private final FptSubjectSkillRepository fptSubjectSkillRepository;
    private final FptSubjectResourceRepository fptSubjectResourceRepository;
    private final FptSubjectCloRepository fptSubjectCloRepository;
    private final FptCurriculumRepository fptCurriculumRepository;
    private final FptCurriculumSubjectRepository fptCurriculumSubjectRepository;

    @Override
    @Transactional
    public ImportSummary importOverlay(JsonNode root, CurriculumRef ref) {
        JsonNode subjects = root.path("subjects");
        if (!subjects.isArray()) {
            log.warn("FptOverlayImport: overlay has no 'subjects' array. Nothing imported.");
            return new ImportSummary(0, 0, 0, 0, 0, 0);
        }

        FptCurriculum curriculum = upsertCurriculum(ref);
        // Rebuild only THIS curriculum's term mapping; other curricula are untouched.
        fptCurriculumSubjectRepository.deleteByCurriculumId(curriculum.getId());

        int subjectCount = 0;
        int skillCount = 0;
        int resourceCount = 0;
        int cloCount = 0;
        int unmatchedSkills = 0;
        int preservedSubjects = 0;
        for (JsonNode s : subjects) {
            String code = s.path("code").asText("").trim();
            if (code.isEmpty()) continue;

            // Shared, cohort-independent subject facts — deduplicated by code (upsert).
            fptSubjectRepository.save(FptSubject.builder()
                    .code(code)
                    .name(textOr(s, "name", code))
                    .credits(s.hasNonNull("credits") ? s.get("credits").asInt() : null)
                    .prerequisite(textOr(s, "prerequisite", null))
                    .description(textOr(s, "description", null))
                    .build());
            subjectCount++;

            // Per-curriculum term placement and combo membership. An overlay predating
            // combo support has no combo_code, which reads as "trunk" — the same subject
            // set everyone saw before, rather than a curriculum that suddenly looks empty.
            fptCurriculumSubjectRepository.save(FptCurriculumSubject.builder()
                    .curriculumId(curriculum.getId())
                    .subjectCode(code)
                    .semester(s.hasNonNull("semester") ? s.get("semester").asInt() : null)
                    .comboCode(textOr(s, "combo_code", null))
                    .comboName(textOr(s, "combo_name", null))
                    .build());

            // Rebuild a facet only when the overlay actually carries it. Deleting first and
            // re-inserting nothing is how an empty scrape erases good data: on 2026-07-22 a
            // sync returned 47 bare subjects and took every CLO and every mirrored file with
            // it, orphaning files that were still sitting in storage.
            boolean carriedAnything = false;

            if (hasEntries(s, "skills")) {
                carriedAnything = true;
                fptSubjectSkillRepository.deleteBySubjectCode(code);
                for (JsonNode sk : s.path("skills")) {
                    String skillName = sk.asText("").trim();
                    if (skillName.isEmpty()) continue;
                    Skill skill = skillRepository.findBySkillName(skillName);
                    if (skill == null) unmatchedSkills++;
                    fptSubjectSkillRepository.save(FptSubjectSkill.builder()
                            .subjectCode(code)
                            .skillId(skill != null ? skill.getSkillId() : null)
                            .skillName(skillName)
                            .build());
                    skillCount++;
                }
            }

            if (hasEntries(s, "clos")) {
                carriedAnything = true;
                fptSubjectCloRepository.deleteBySubjectCode(code);
                cloCount += importSubjectClos(code, s);
            }

            if (hasEntries(s, "materials") || hasEntries(s, "sessions")) {
                carriedAnything = true;
                fptSubjectResourceRepository.deleteBySubjectCode(code);
                resourceCount += importSubjectResources(code, s);
            }

            if (!carriedAnything) {
                preservedSubjects++;
            }
        }

        log.info("FptOverlayImport: curriculum {} — {} subjects, {} skill links ({} unmatched), "
                        + "{} CLOs, {} resources. {} subject(s) carried no detail; their stored "
                        + "skills/CLOs/resources were left untouched.",
                curriculum.getCode(), subjectCount, skillCount, unmatchedSkills, cloCount,
                resourceCount, preservedSubjects);
        return new ImportSummary(subjectCount, skillCount, unmatchedSkills, resourceCount,
                cloCount, preservedSubjects);
    }

    /** True when the overlay carries at least one entry under {@code field} for this subject. */
    private static boolean hasEntries(JsonNode subject, String field) {
        JsonNode node = subject.path(field);
        return node.isArray() && node.size() > 0;
    }

    /** Upsert the curriculum row by code, parsing program/cohort/batch from the code. */
    private FptCurriculum upsertCurriculum(CurriculumRef ref) {
        String code = ref.code() == null || ref.code().isBlank() ? "UNKNOWN" : ref.code().trim();
        FptCurriculum curriculum = fptCurriculumRepository.findByCode(code)
                .orElseGet(() -> FptCurriculum.builder().code(code).build());

        String[] parts = code.split("_");
        curriculum.setProgram(parseProgram(parts));
        curriculum.setCohort(parseCohort(parts));
        curriculum.setBatch(parseBatch(parts));
        if (ref.curid() != null && !ref.curid().isBlank()) {
            curriculum.setCurid(ref.curid().trim());
        }
        curriculum.setSyncedAt(LocalDateTime.now());

        if (ref.makeDefault()) {
            // Exactly one default at a time.
            fptCurriculumRepository.findFirstByIsDefaultTrue().ifPresent(existing -> {
                if (!existing.getCode().equals(code)) {
                    existing.setDefault(false);
                    fptCurriculumRepository.save(existing);
                }
            });
            curriculum.setDefault(true);
        }
        return fptCurriculumRepository.save(curriculum);
    }

    /** Program slug: the token between the degree prefix and the cohort token (e.g. "SE"). */
    private static String parseProgram(String[] parts) {
        int cohortIdx = cohortIndex(parts);
        if (cohortIdx > 1) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < cohortIdx; i++) {
                if (sb.length() > 0) sb.append('_');
                sb.append(parts[i]);
            }
            return sb.toString();
        }
        return parts.length > 1 ? parts[1] : null;
    }

    private static Integer parseCohort(String[] parts) {
        int idx = cohortIndex(parts);
        if (idx < 0) return null;
        Matcher m = COHORT_TOKEN.matcher(parts[idx]);
        return m.matches() ? Integer.valueOf(m.group(1)) : null;
    }

    /** Batch: the letters after the cohort digits, plus any trailing tokens (e.g. "D_K20A"). */
    private static String parseBatch(String[] parts) {
        int idx = cohortIndex(parts);
        if (idx < 0) return null;
        Matcher m = COHORT_TOKEN.matcher(parts[idx]);
        StringBuilder sb = new StringBuilder();
        if (m.matches() && !m.group(2).isEmpty()) sb.append(m.group(2));
        for (int i = idx + 1; i < parts.length; i++) {
            if (sb.length() > 0) sb.append('_');
            sb.append(parts[i]);
        }
        String batch = sb.toString();
        return batch.isEmpty() ? null : batch;
    }

    private static int cohortIndex(String[] parts) {
        for (int i = 0; i < parts.length; i++) {
            if (COHORT_TOKEN.matcher(parts[i]).matches()) return i;
        }
        return -1;
    }

    /**
     * Stores a subject's CLOs verbatim. An overlay produced before the scraper carried
     * them simply has no "clos" key, which leaves the subject with none rather than
     * failing the import.
     */
    private int importSubjectClos(String code, JsonNode subject) {
        int count = 0;
        int order = 0;
        for (JsonNode c : subject.path("clos")) {
            String cloCode = textOr(c, "code", null);
            String outcome = textOr(c, "outcome", null);
            if (cloCode == null || outcome == null) continue;
            fptSubjectCloRepository.save(FptSubjectClo.builder()
                    .subjectCode(code)
                    .code(cloCode)
                    .outcome(outcome)
                    .orderIndex(order++)
                    .build());
            count++;
        }
        return count;
    }

    /** Flattens a subject's materials[] and sessions[] into fpt_subject_resources rows. */
    private int importSubjectResources(String code, JsonNode subject) {
        int count = 0;
        int order = 0;
        for (JsonNode m : subject.path("materials")) {
            String title = firstNonBlank(textOr(m, "description", null), textOr(m, "url", null));
            if (title == null) continue;
            fptSubjectResourceRepository.save(FptSubjectResource.builder()
                    .subjectCode(code)
                    .kind(FptResourceKind.MATERIAL)
                    .title(title)
                    .url(textOr(m, "url", null))
                    .orderIndex(order++)
                    .build());
            count++;
        }
        order = 0;
        for (JsonNode ses : subject.path("sessions")) {
            String topic = textOr(ses, "topic", null);
            if (topic == null || topic.isBlank()) continue;
            String session = textOr(ses, "session", "");
            fptSubjectResourceRepository.save(FptSubjectResource.builder()
                    .subjectCode(code)
                    .kind(FptResourceKind.SESSION)
                    .title(session.isBlank() ? topic : "Session " + session + ": " + topic)
                    // "url" is the syllabus's own reference link; the harvested file goes to
                    // sourceUrl, which never reaches a client — students are served our
                    // mirrored copy through a signed URL after the FPT check.
                    .url(textOr(ses, "url", null))
                    .sourceUrl(textOr(ses, "download", null))
                    .topic(topic)
                    .cloRef(textOr(ses, "lo", null))
                    .orderIndex(order++)
                    .build());
            count++;
        }
        return count;
    }

    private static String textOr(JsonNode node, String field, String fallback) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return fallback;
        String text = v.asText("").trim();
        return text.isEmpty() ? fallback : text;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }
}
