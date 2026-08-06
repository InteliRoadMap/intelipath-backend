package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.domain.entity.CareerRequiredSkill;
import com.inteliroadmap.backend.domain.entity.CareerRole;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.enums.ImportanceLevel;
import com.inteliroadmap.backend.repositories.CareerRequiredSkillRepository;
import com.inteliroadmap.backend.repositories.RecruitmentSkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Adds to a career's skill catalog the things employers actually ask that career for.
 *
 * <p>{@code career_required_skills} was written by hand and the omissions follow no
 * rule. Measured: {@code Git} is required by Data Science, DevOps, Frontend, Full Stack
 * and QA but not Backend; {@code CI/CD} is not required by DevOps; {@code Testing} is
 * not required by QA. Patching holes like that one at a time is guesswork dressed as
 * maintenance — the only durable answer is to stop writing the table by hand.
 *
 * <p>So a skill enters a career's catalog because postings for that career asked for
 * it, and the row can be traced back to the postings that put it there.
 *
 * <p><b>Additive only.</b> Nothing is removed. The catalog is also the roadmap's
 * curriculum, and a skill with no market demand is not thereby worthless to learn —
 * absence of postings is absence of evidence, and this component has no business
 * deleting teaching material on the strength of it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CareerSkillDemandDeriver {

    /**
     * Postings required before a skill is claimed as part of a career.
     *
     * <p>Two, not one. A single posting can be a typo, a copy-paste from another advert,
     * or one employer's idiosyncrasy, and each wrong row is a skill students are told
     * their career needs. Two independent employers is a low bar that still costs a
     * fluke its vote.
     */
    static final int MIN_POSTINGS = 2;

    /** At least this share of the career's most-asked-for skill: a defining skill. */
    static final double HIGH_SHARE = 0.20;
    /** At least this share of the leader: worth naming, not defining. */
    static final double AVG_SHARE = 0.05;

    private final RecruitmentSkillRepository recruitmentSkillRepository;
    private final CareerRequiredSkillRepository careerRequiredSkillRepository;
    private final CoreSkillEligibility coreSkillEligibility;

    /**
     * @return how many rows were added
     */
    @Transactional
    public int deriveFromMarket() {
        List<Object[]> rows = recruitmentSkillRepository.demandByCareer();
        if (rows.isEmpty()) {
            log.info("CareerSkillDemandDeriver: no per-posting skills recorded yet; nothing to derive. "
                    + "Run the extraction first.");
            return 0;
        }

        // The most-asked-for skill in each career, which is what the share below is
        // measured against — NOT the number of postings that career has.
        //
        // Stated precisely because the two are easy to confuse and the difference
        // changes every grade. Against posting count, a skill in 20 of 133 Backend
        // postings scores 15%; against the career's leader it scores relative to
        // whatever the strongest signal in that career happens to be. The leader is the
        // right denominator here for the same reason CareerSkillMarketGrader uses it:
        // extraction recall differs wildly between careers, so "how does this compare to
        // the best-attested skill in the same corpus" survives that, while "what
        // fraction of postings" reads a recall difference as a demand difference.
        java.util.Map<UUID, Integer> leaderByCareer = new java.util.HashMap<>();
        for (Object[] row : rows) {
            UUID careerId = (UUID) row[0];
            int postings = ((Number) row[3]).intValue();
            leaderByCareer.merge(careerId, postings, Math::max);
        }

        Set<String> existing = new HashSet<>(careerRequiredSkillRepository.findExistingCareerSkillKeys());

        List<CareerRequiredSkill> added = new ArrayList<>();
        for (Object[] row : rows) {
            UUID careerId = (UUID) row[0];
            UUID skillId = (UUID) row[1];
            String skillName = (String) row[2];
            int postings = ((Number) row[3]).intValue();

            if (postings < MIN_POSTINGS || existing.contains(key(careerId, skillId))) {
                continue;
            }
            double share = (double) postings / Math.max(1, leaderByCareer.getOrDefault(careerId, 1));
            // A category word can out-count every real skill and still teach nobody
            // anything: measured this run, the extractor put "Cloud" in 84 postings and
            // "API" in 80, either of which would enter as HIGH here on share alone. Grade
            // them AVG at most, so they stay in the catalog as context without becoming
            // part of the readiness denominator. Cheaper than letting them in and having
            // CareerCoreSkillDemoter take them out again on the next run.
            boolean coreEligible = coreSkillEligibility.isCoreEligible(skillName);
            added.add(CareerRequiredSkill.builder()
                    // Id-only references: these rows are inserted in bulk and never read
                    // back here, so loading the full career and skill would be work spent
                    // on nothing.
                    .careerRole(CareerRole.builder().careerId(careerId).build())
                    .skill(Skill.builder().skillId(skillId).build())
                    .importanceLevel(share >= HIGH_SHARE && coreEligible ? ImportanceLevel.HIGH
                            : share >= AVG_SHARE ? ImportanceLevel.AVG : ImportanceLevel.LOW)
                    .build());
            existing.add(key(careerId, skillId));
            log.debug("CareerSkillDemandDeriver: '{}' enters career {} on {} posting(s)",
                    skillName, careerId, postings);
        }

        careerRequiredSkillRepository.saveAll(added);
        log.info("CareerSkillDemandDeriver: added {} career-skill row(s) from measured demand "
                + "(threshold {} posting(s))", added.size(), MIN_POSTINGS);
        return added.size();
    }

    private static String key(UUID careerId, UUID skillId) {
        return careerId + "|" + skillId;
    }
}
