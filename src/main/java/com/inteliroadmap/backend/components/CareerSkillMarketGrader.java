package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.repositories.CareerRequiredSkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grades every career's required skills against the job postings, so the grade
 * follows the market instead of the seed file.
 *
 * <p><b>Why this is code and not a migration.</b> {@code skill_trends} is rebuilt
 * from the posting corpus every night by {@code SkillExtractionScheduler}. A
 * hand-written regrade is true on the day it runs and quietly wrong from the next
 * crawl onwards — which is how the catalog reached the state that broke GitHub
 * import: Backend filed Python (193 postings), Java (158) and PostgreSQL (53) as
 * LOW while Tensorflow (2) and Ruby on Rails (0) sat at HIGH. Fixing those rows by
 * hand fixes today and rebuilds the same fault over the following weeks. So the
 * grading runs immediately after the trends it reads.
 *
 * <p><b>Scaled per career, not globally.</b> Backend's leading skill is named by
 * 70 of Backend's own postings and Frontend's by 22 of Frontend's. One absolute
 * threshold would file most of Frontend as LOW for reasons that have nothing to do
 * with Frontend, so each career is measured against its own leader.
 *
 * <p><b>Counted per career too — this was wrong until 2026-08-06.</b> The scaling above
 * was in place from the start, but the numbers being scaled came from
 * {@code skill_trends}, which has no career dimension. Every career was ranked by the
 * whole market and then divided by a different denominator, which changes the cut and not
 * the order. Frontend's core skills came out as Agile, SQL, Java and AWS, with no
 * TypeScript, HTML5 or CSS3 — while Frontend's own adverts name JavaScript 22 times,
 * React 18, HTML5 15 and CSS3 14. The count now comes from {@code recruitment_skills}
 * joined to the posting's own {@code career_id}. See
 * {@code CareerRequiredSkillRepository#regradeByMarketDemand}.
 *
 * <p><b>Silence is not a demotion.</b> A skill with no posting evidence keeps
 * whatever grade it had. Absence here means the crawler never saw the term, not
 * that employers do not want it — {@code bcrypt} and {@code OWASP Risks} are real
 * backend topics that no advert spells out. Only skills the market did speak about
 * are re-graded, in either direction.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CareerSkillMarketGrader {

    /** Share of the career's leading skill at or above which a skill is HIGH. */
    static final double HIGH_RATIO = 0.25;

    /** Share at or above which a skill is AVG. Below it, LOW. */
    static final double AVG_RATIO = 0.05;

    private final CareerRequiredSkillRepository careerRequiredSkillRepository;

    /**
     * Re-grades every career against the current trends.
     *
     * <p>Runs as one statement rather than a read-modify-write loop: the catalog is
     * ~3.700 rows across eight careers and the grade of each depends on that
     * career's maximum, which SQL can compute in the same pass.
     *
     * @return how many rows changed grade
     */
    @Transactional
    public int regradeFromMarket() {
        int changed = careerRequiredSkillRepository.regradeByMarketDemand(HIGH_RATIO, AVG_RATIO);
        log.info("CareerSkillMarketGrader: re-graded {} career-skill row(s) against the posting corpus "
                + "(HIGH at >= {}% of the career leader, AVG at >= {}%).",
                changed, Math.round(HIGH_RATIO * 100), Math.round(AVG_RATIO * 100));
        return changed;
    }
}
