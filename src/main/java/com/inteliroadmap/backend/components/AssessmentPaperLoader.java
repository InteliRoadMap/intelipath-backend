package com.inteliroadmap.backend.components;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.model.AssessmentItem;
import com.inteliroadmap.backend.domain.model.AssessmentPaper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Loads the graded assessment papers and matches one to a career.
 *
 * <h2>Why the bank is a file and not a table</h2>
 *
 * <p>This backend runs {@code ddl-auto: none} with no migration tool, so a new table
 * has to be applied by hand to three separate targets and stays a liability every
 * time the project is re-seeded. Against that, the bank needs no relational query —
 * it is read whole, once, at startup — and {@code student_assessments.questions} and
 * {@code answers} are already {@code jsonb}, so a served paper and a student's
 * responses fit the existing schema untouched.
 *
 * <p>The deciding argument is review. A question bank is content with right answers
 * in it, and a wrong answer key silently mis-grades every student who meets it. In
 * {@code resources/assessment} it is diffed, reviewed and versioned like code; in a
 * table it is invisible until someone runs a query.
 *
 * <h2>What loading does beyond parsing</h2>
 *
 * <p>Each item names the catalog skills it probes as plain text, and those names are
 * resolved to real {@code skill_id}s here through {@link SkillNameCanonicalizer} —
 * the same identity function the extractor and the seeder use, so {@code "Node.js"}
 * in the bank and {@code "NodeJS"} in the catalog are one skill. Resolution happens
 * once, at startup, rather than on every assessment.
 *
 * <p>A name with no catalog row is <b>not</b> an error. The item still scores; it
 * simply contributes no skill evidence. Failing startup over a missing skill would
 * make the whole assessment hostage to catalog drift, and silently dropping the item
 * would quietly shorten the paper — so the loader logs it and carries on.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AssessmentPaperLoader {

    /** Files to load, relative to {@code src/main/resources}. */
    private static final List<String> PAPER_RESOURCES = List.of(
            "assessment/backend.json",
            "assessment/frontend.json",
            "assessment/fullstack.json");

    private final ObjectMapper objectMapper;
    private final SkillNameCanonicalizer skillNameCanonicalizer;

    /** Career name, lower-cased, to the paper it is served. */
    private volatile Map<String, AssessmentPaper> papersByCareerName = Map.of();

    @PostConstruct
    void load() {
        Map<String, AssessmentPaper> byCareer = new HashMap<>();
        for (String resource : PAPER_RESOURCES) {
            try {
                AssessmentPaper paper = read(resource);
                if (paper == null) continue;
                AssessmentPaper resolved = withResolvedSkills(paper);
                for (String careerName : paper.careerNames()) {
                    byCareer.put(careerName.toLowerCase(Locale.ROOT), resolved);
                }
                log.info("AssessmentPaperLoader: loaded {} v{} — {} item(s), {} point(s), careers {}.",
                        paper.scope(), paper.version(), paper.items().size(), paper.totalWeight(),
                        paper.careerNames());
            } catch (Exception e) {
                // One malformed paper must not stop the application: the careers it
                // covers fall back to the self-report form, which is a worse
                // assessment and not a broken one.
                log.error("AssessmentPaperLoader: could not load {} — that career falls back to "
                        + "the self-report form. Cause: {}", resource, e.toString());
            }
        }
        papersByCareerName = Map.copyOf(byCareer);
    }

    /**
     * The paper for a career, if one exists.
     *
     * <p>Empty is the normal answer for the five careers with no bank yet — Data
     * Science, DevOps, Game Developer, QA, Software Architect — and the caller serves
     * the self-report form for those.
     */
    public Optional<AssessmentPaper> paperFor(String careerName) {
        if (careerName == null || careerName.isBlank()) return Optional.empty();
        return Optional.ofNullable(papersByCareerName.get(careerName.trim().toLowerCase(Locale.ROOT)));
    }

    /** One item of a served paper, by id — how a submitted answer finds its key. */
    public Optional<AssessmentItem> itemOf(AssessmentPaper paper, String itemId) {
        if (paper == null || itemId == null) return Optional.empty();
        return paper.items().stream().filter(item -> itemId.equals(item.id())).findFirst();
    }

    private AssessmentPaper read(String resource) throws Exception {
        ClassPathResource classPathResource = new ClassPathResource(resource);
        if (!classPathResource.exists()) {
            log.warn("AssessmentPaperLoader: {} is not on the classpath.", resource);
            return null;
        }
        try (InputStream in = classPathResource.getInputStream()) {
            return objectMapper.readValue(in, AssessmentPaper.class);
        }
    }

    private AssessmentPaper withResolvedSkills(AssessmentPaper paper) {
        List<AssessmentItem> resolved = new ArrayList<>(paper.items().size());
        for (AssessmentItem item : paper.items()) {
            List<UUID> ids = new ArrayList<>();
            for (String name : item.skills() == null ? List.<String>of() : item.skills()) {
                Skill skill = skillNameCanonicalizer.resolve(name);
                if (skill == null) {
                    log.warn("AssessmentPaperLoader: item {} names skill '{}', which has no catalog "
                            + "row. The item still scores; it just yields no evidence for that skill.",
                            item.id(), name);
                    continue;
                }
                ids.add(skill.getSkillId());
            }
            resolved.add(item.withSkillIds(List.copyOf(ids)));
        }
        return new AssessmentPaper(paper.scope(), paper.careerNames(), paper.version(), List.copyOf(resolved));
    }
}
