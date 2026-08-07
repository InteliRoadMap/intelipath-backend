package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.repositories.CareerRequiredSkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Takes out of the HIGH set every row a student cannot actually be measured on.
 *
 * <h2>Why the HIGH set matters more than it looks</h2>
 *
 * <p>{@code SeniorityCalculator.CORE_GRADES} is {@code {HIGH}} and nothing else. That one
 * line makes the HIGH set three things at once: the denominator of every readiness
 * percentage, the population of the Skill Map, and the list a roadmap presents as "what
 * this career needs". A row that names nothing measurable does not sit there harmlessly —
 * it divides every student's readiness by a larger number for as long as it exists.
 *
 * <p>Measured before this component existed: 45 HIGH rows had zero postings, eight per
 * career, which is the fingerprint of a hand-written seed. Frontend held 26 HIGH rows of
 * which 17 were unmeasurable, so a student who held six of Frontend's nine real core
 * skills read as 23% ready instead of 67%.
 *
 * <h2>Two questions, two sources</h2>
 *
 * <p>{@link CoreSkillEligibility} judges the <b>name</b>: is this one specific nameable
 * thing, or a list, a chapter title, or a category word. That catches 35 of the 45.
 *
 * <p>The rest need the <b>structure</b>, because their names are perfectly well-formed and
 * still name nothing a student can hold:
 *
 * <ul>
 *   <li><b>Containers.</b> {@code Package Managers} has eight child nodes,
 *       {@code Web Components} three, {@code Progressive Web Apps} two. The student is
 *       measured on the children; the parent is scaffolding.
 *   <li><b>Rows attached to nothing.</b> No posting names them <em>and</em> no roadmap
 *       teaches them. They measure nothing and teach nothing.
 * </ul>
 *
 * <p>Note the {@code AND} in the second case. A skill on no roadmap that the market does
 * ask for is a gap in the curriculum, not junk, and stays HIGH so the roadmap keeps being
 * told about it.
 *
 * <h2>Demote, never delete</h2>
 *
 * <p>Rows drop to AVG and stay in the table. {@code Web Security and OWASP} is a good
 * thing to teach; it is simply not a thing to divide a percentage by. This is the same
 * line {@link CareerSkillDemandDeriver} already draws — <i>absence of postings is absence
 * of evidence</i> — applied to grading rather than to insertion.
 *
 * <h2>Ordering</h2>
 *
 * <p>Must run <b>after</b> {@link CareerSkillMarketGrader}. That component's SQL inner-joins
 * {@code skill_trends}, so it never touches a row with no market data and cannot undo a
 * demotion made here. Run it the other way round and the grader would re-promote whatever
 * the market did name, which for {@code DevOps} (132 postings) and {@code Cloud} (84) is
 * precisely the rows this exists to remove.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CareerCoreSkillDemoter {

    private final CareerRequiredSkillRepository careerRequiredSkillRepository;
    private final CoreSkillEligibility coreSkillEligibility;

    /**
     * @return how many rows were demoted
     */
    @Transactional
    public int demoteUnmeasurableCoreSkills() {
        List<Object[]> rows = careerRequiredSkillRepository.findHighRowsWithEvidence();
        if (rows.isEmpty()) {
            return 0;
        }

        List<UUID> byName = new ArrayList<>();
        List<UUID> byStructure = new ArrayList<>();
        for (Object[] row : rows) {
            UUID rowId = (UUID) row[0];
            String skillName = (String) row[2];
            long postings = ((Number) row[3]).longValue();
            long nodeCount = ((Number) row[4]).longValue();
            long childCount = ((Number) row[5]).longValue();

            if (!coreSkillEligibility.isCoreEligible(skillName)) {
                byName.add(rowId);
            } else if (postings == 0 && (childCount > 0 || nodeCount == 0)) {
                byStructure.add(rowId);
            }
        }

        List<UUID> doomed = new ArrayList<>(byName);
        doomed.addAll(byStructure);
        if (doomed.isEmpty()) {
            log.info("CareerCoreSkillDemoter: all {} HIGH row(s) name something measurable.", rows.size());
            return 0;
        }

        int demoted = careerRequiredSkillRepository.demoteToAvg(doomed);
        log.info("CareerCoreSkillDemoter: demoted {} of {} HIGH row(s) to AVG - {} for naming a list, "
                        + "a chapter title or a category, {} for having no market evidence and being "
                        + "either a container node or on no roadmap at all. Nothing was deleted.",
                demoted, rows.size(), byName.size(), byStructure.size());
        return demoted;
    }
}
