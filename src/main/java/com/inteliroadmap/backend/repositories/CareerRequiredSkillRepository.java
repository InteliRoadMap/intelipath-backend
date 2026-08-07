package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.CareerRequiredSkill;
import com.inteliroadmap.backend.domain.entity.CareerRole;
import com.inteliroadmap.backend.domain.enums.ImportanceLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface CareerRequiredSkillRepository extends JpaRepository<CareerRequiredSkill, UUID> {
    List<CareerRequiredSkill> findByCareerRole_CareerId(UUID careerId);

    /**
     * The skills a role genuinely requires, as opposed to every skill tagged with
     * it.
     *
     * <p>{@code career_required_skills} is not a list of requirements — it is the
     * whole catalog scoped by career, with each row graded. Frontend holds 504
     * rows of which 29 are HIGH and 91 AVG; Backend holds 1.521 of which 29 are
     * HIGH. Dividing anything by the full count buries every student at the
     * bottom of every scale, so the core set (HIGH + AVG) is what level and gap
     * maths must run against. LOW rows stay in the table as "related".
     */
    List<CareerRequiredSkill> findByCareerRole_CareerIdAndImportanceLevelIn(
            UUID careerId, Collection<ImportanceLevel> importanceLevels);

    boolean existsByCareerRole_CareerIdAndSkill_SkillId(UUID careerId, UUID skillId);

    /**
     * How many distinct careers list each skill — the document frequency of the
     * TF-IDF weighting in {@code MarketDemandService}.
     *
     * <p>A skill named by one career discriminates between roles; a skill named by
     * all of them does not, however often the market asks for it. This is the
     * count that turns "frequently required" into "frequently required <em>and</em>
     * characteristic".
     *
     * @return rows of {@code [skillId, careerCount]}
     */
    /**
     * Every career's rows at the given importance grades, in one query.
     *
     * <p>Career affinity compares the student against all eight roles at once;
     * asking per career would issue eight round-trips to answer one question.
     */
    List<CareerRequiredSkill> findByImportanceLevelIn(Collection<ImportanceLevel> importanceLevels);

    /**
     * The career's skills, strongest market evidence first, for prompting a model.
     *
     * <p>Ordered rather than filtered, and capped by the caller. {@code
     * findByCareerRole_CareerId} returns 1.479 rows for Backend — the roadmap tree
     * flattened, `$match` and `path module` included — and handing that to an LLM as
     * "the skills to choose from" got an empty answer back for three consecutive
     * Spring Boot repositories. Too many candidates, almost all irrelevant.
     *
     * <p>Ordering leads on posting counts because that is the only column here
     * measured against reality; importance is a fallback for the skills the crawler
     * never saw a term for, and name is last so the order does not wobble between
     * requests. Deliberately not filtered to HIGH+AVG: before the market regrade
     * that filter excluded Java, Spring Boot and PostgreSQL from Backend, and a
     * ranking that puts them first is safe even if the grades drift again.
     *
     * @return skill names, best first
     */
    @Query(value = """
            SELECT s.skill_name
            FROM career_required_skills crs
            JOIN skills s ON s.skill_id = crs.skill_id
            LEFT JOIN (
                SELECT skill_id, sum(coalesce(jobs_needed, 0)) AS jobs
                FROM skill_trends GROUP BY skill_id
            ) t ON t.skill_id = crs.skill_id
            WHERE crs.career_id = :careerId
            ORDER BY coalesce(t.jobs, 0) DESC,
                     CASE upper(coalesce(crs.importance_level, 'LOW'))
                         WHEN 'HIGH' THEN 0 WHEN 'AVG' THEN 1 ELSE 2 END,
                     s.skill_name
            LIMIT :limit
            """, nativeQuery = true)
    List<String> findRankedSkillNames(@Param("careerId") UUID careerId, @Param("limit") int limit);

    /**
     * Existing (career, skill) pairs as {@code "<careerId>|<skillId>"}.
     *
     * <p>A projection rather than {@code findAll()}: the entity holds its career and
     * skill as lazy associations, so reading the ids off every row would fire two
     * queries per row to learn something the join table already stores.
     */
    @Query(value = "SELECT career_id || '|' || skill_id FROM career_required_skills", nativeQuery = true)
    List<String> findExistingCareerSkillKeys();

    /**
     * Re-grades importance from the postings <em>for that career</em>.
     *
     * <p><b>Corrected source.</b> This used to read {@code skill_trends}, which has no
     * career dimension — it is one row per skill per day for the whole market. Every
     * career was therefore graded against the same global ranking, and the per-career
     * scaling below only divided that ranking by a different number. Measured after the
     * catalog cleanup made it visible: Frontend's HIGH set came out as Agile (226), SQL
     * (176), Java (158), AWS (113), JavaScript, Microservices, React — no TypeScript, no
     * HTML5, no CSS3, because those score 55, 38 and 29 globally and the cut was 25% of
     * 226. Frontend's own adverts say something completely different: JavaScript 22,
     * React 18, HTML5 15, CSS3 14, Vue 12, TypeScript 11, Angular 7.
     *
     * <p>This is the same fault F4 fixed in the <em>ranking</em> path and left standing in
     * the <em>grading</em> path — "one list for all eight careers", surviving in the one
     * place that decides what a career's core skills are. {@code recruitment_skills}
     * carries the posting, and {@code recruitments.career_id} carries which career the
     * posting is for, so the question can now be asked properly.
     *
     * <p><b>Silence is still not a demotion.</b> A (career, skill) pair no posting for
     * that career ever named has no row in {@code demand} and is left exactly as it was.
     *
     * <p><b>Known limitation.</b> {@code RecruitmentCareerClassifier} labels titles by
     * keyword and currently classifies 504 of 913 postings, so 409 are invisible here.
     * That thins every career's corpus — Frontend's leader is 22 postings and Game
     * Developer's is 5 — which makes the grades noisier, not wronger: a thin sample of
     * the right question beats a large sample of the wrong one.
     *
     * <p>Thresholds come in as parameters rather than being written into the SQL so
     * {@link com.inteliroadmap.backend.components.CareerSkillMarketGrader} stays the
     * single place they are defined.
     *
     * @return rows whose grade actually changed
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            WITH demand AS (
                SELECT r.career_id, rs.skill_id, count(*) AS jobs
                FROM recruitment_skills rs
                JOIN recruitments r ON r.recruitment_id = rs.recruitment_id
                WHERE r.career_id IS NOT NULL
                GROUP BY r.career_id, rs.skill_id
            ), leader AS (
                SELECT career_id, max(jobs) AS top_jobs FROM demand GROUP BY career_id
            ), graded AS (
                SELECT d.career_id, d.skill_id,
                       CASE WHEN d.jobs >= :highRatio * l.top_jobs THEN 'HIGH'
                            WHEN d.jobs >= :avgRatio * l.top_jobs THEN 'AVG'
                            ELSE 'LOW' END AS new_importance
                FROM demand d JOIN leader l ON l.career_id = d.career_id
            )
            UPDATE career_required_skills crs
            SET importance_level = g.new_importance
            FROM graded g
            WHERE g.career_id = crs.career_id
              AND g.skill_id = crs.skill_id
              AND crs.importance_level IS DISTINCT FROM g.new_importance
            """, nativeQuery = true)
    int regradeByMarketDemand(@Param("highRatio") double highRatio, @Param("avgRatio") double avgRatio);

    @Query("SELECT crs.skill.skillId, COUNT(DISTINCT crs.careerRole.careerId) "
            + "FROM CareerRequiredSkill crs "
            + "WHERE crs.skill IS NOT NULL "
            + "GROUP BY crs.skill.skillId")
    List<Object[]> countCareersPerSkill();

    /**
     * Every HIGH row with the three facts needed to judge whether it belongs there.
     *
     * <p>HIGH is the readiness denominator and the Skill Map's population — see
     * {@code SeniorityCalculator.CORE_GRADES} — so what sits in it has to be something a
     * student can be measured on. Three facts decide that, and none of them is available
     * from the row alone:
     *
     * <ul>
     *   <li>{@code postings} — did the market ever name it
     *   <li>{@code node_count} — does any roadmap teach it
     *   <li>{@code child_count} — is it a container other nodes hang from, i.e. a chapter
     *       title rather than a thing to learn
     * </ul>
     *
     * <p>Counted with scalar sub-selects rather than joins: three {@code LEFT JOIN ...
     * GROUP BY}s over the same base rows would multiply them together before collapsing,
     * and the counts would come back wrong in a way that is easy to miss and hard to spot.
     *
     * @return rows of {@code [skillRequiredId, skillId, skillName, postings, nodeCount,
     *         childCount]}
     */
    @Query(value = """
            SELECT crs.skill_required_id, s.skill_id, s.skill_name,
                   (SELECT count(*) FROM recruitment_skills rs WHERE rs.skill_id = s.skill_id) AS postings,
                   (SELECT count(*) FROM skill_nodes n WHERE n.skill_id = s.skill_id) AS node_count,
                   (SELECT count(*) FROM skill_nodes c
                      JOIN skill_nodes p ON p.node_id = c.parent_node
                     WHERE p.skill_id = s.skill_id) AS child_count
            FROM career_required_skills crs
            JOIN skills s ON s.skill_id = crs.skill_id
            WHERE upper(coalesce(crs.importance_level, '')) = 'HIGH'
            """, nativeQuery = true)
    List<Object[]> findHighRowsWithEvidence();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE career_required_skills SET importance_level = 'AVG' "
            + "WHERE skill_required_id IN (:ids)", nativeQuery = true)
    int demoteToAvg(@Param("ids") Collection<UUID> ids);
}
