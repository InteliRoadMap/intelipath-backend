-- ============================================================================
-- Core skills graded by the career's own postings, not by the whole market
--
-- WHY
-- ---
-- SeniorityCalculator.CORE_GRADES is {HIGH} and nothing else, so the HIGH set is
-- the readiness denominator, the Skill Map's population, and the list a roadmap
-- presents as "what this career needs". Two faults were putting the wrong rows
-- in it, and the second only became visible once the first was fixed.
--
-- 1. UNMEASURABLE ROWS. 45 HIGH rows had zero postings behind them - eight per
--    career, the fingerprint of a hand-written seed. Frontend held 26 HIGH rows
--    of which 17 were chapter titles ("Build Tools and Bundlers", "Web Security
--    and OWASP") or rows attached to no roadmap at all.
--
-- 2. GLOBAL GRADING. CareerSkillMarketGrader read skill_trends, which has no
--    career dimension: one row per skill per day for the entire market. Every
--    career was ranked by the same global list and then divided by a different
--    denominator, which moves the cut and not the order. With the 17 titles
--    cleared away, Frontend's core came out as:
--
--        Agile 226 | SQL 176 | Java 158 | AWS 113 | JavaScript | Microservices | React
--
--    No TypeScript, no HTML5, no CSS3 - they score 55, 38 and 29 globally and
--    the cut was 25% of 226. Frontend's OWN adverts say:
--
--        JavaScript 22 | React 18 | HTML5 15 | CSS3 14 | Vue 12 | TypeScript 11 | Angular 7
--
--    This is the fault F4 fixed in the ranking path and left standing in the
--    grading path, in the one place that decides a career's core skills.
--
-- WHAT THIS FILE IS
-- -----------------
-- The backfill for both. The running code already does the same thing on every
-- extraction - CareerRequiredSkillRepository.regradeByMarketDemand for step 1,
-- CareerCoreSkillDemoter for step 2 - so this only brings the current data
-- forward without paying for another LLM pass over 913 postings. The thresholds
-- and the rules below are copied from those two, and if either is ever changed,
-- this file is stale rather than authoritative.
--
-- NOTHING IS DELETED. Rows drop to AVG and stay in the table: the catalog is
-- also the roadmap's curriculum, and "Web Security and OWASP" is a good thing to
-- teach - it is simply not a thing to divide a percentage by.
--
-- SILENCE IS NOT A DEMOTION. A (career, skill) pair no posting for that career
-- ever named has no row in `demand` and is left exactly as it was, which is what
-- keeps bcrypt and OWASP Risks - real topics no Vietnamese advert spells out -
-- from being graded away.
--
-- RUN AS ONE TRANSACTION:
--   psql -1 -U intelipath -d intelipath -f 2026-08-06_career_core_regrade.sql
-- ============================================================================

CREATE TABLE IF NOT EXISTS career_core_regrade_undo (
    skill_required_id uuid PRIMARY KEY,
    old_importance    varchar(20),
    changed_at        timestamp NOT NULL DEFAULT now()
);

INSERT INTO career_core_regrade_undo (skill_required_id, old_importance)
SELECT skill_required_id, importance_level FROM career_required_skills
ON CONFLICT (skill_required_id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Step 1 - regrade from the career's own postings.
-- Mirrors CareerRequiredSkillRepository.regradeByMarketDemand
-- with CareerSkillMarketGrader.HIGH_RATIO = 0.25, AVG_RATIO = 0.05.
-- ---------------------------------------------------------------------------
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
           CASE WHEN d.jobs >= 0.25 * l.top_jobs THEN 'HIGH'
                WHEN d.jobs >= 0.05 * l.top_jobs THEN 'AVG'
                ELSE 'LOW' END AS new_importance
    FROM demand d JOIN leader l ON l.career_id = d.career_id
)
UPDATE career_required_skills crs
SET importance_level = g.new_importance
FROM graded g
WHERE g.career_id = crs.career_id
  AND g.skill_id = crs.skill_id
  AND crs.importance_level IS DISTINCT FROM g.new_importance;

-- ---------------------------------------------------------------------------
-- Step 2 - take out of HIGH whatever a student cannot be measured on.
-- Mirrors CareerCoreSkillDemoter + CoreSkillEligibility. Strictly after step 1:
-- step 1 only touches rows the market named, so it cannot undo this, whereas the
-- reverse order would re-promote exactly the rows this removes.
--
-- Rule A - the name. A list (" & ", " and ", " / "), a chapter title (ending in
-- Fundamentals, Basics, Techniques, ... ), a category word, or a string too long
-- to be a name at all.
--
-- Rule B - the structure, for names that are well-formed and still name nothing
-- holdable: no market evidence AND (a container other nodes hang from, OR on no
-- roadmap at all). Note the AND - a skill on no roadmap that the market DOES ask
-- for is a gap in the curriculum, not junk, and stays HIGH so the roadmap keeps
-- being told about it.
-- ---------------------------------------------------------------------------
UPDATE career_required_skills crs
SET importance_level = 'AVG'
FROM skills s
WHERE s.skill_id = crs.skill_id
  AND upper(coalesce(crs.importance_level, '')) = 'HIGH'
  AND (
        -- Rule A
        s.skill_name ~* '\s(&|and|/|\+|,|or)\s'
     OR length(s.skill_name) > 40
     OR lower(regexp_replace(s.skill_name, '^.*\s', '')) IN (
            'fundamentals','basics','essentials','introduction','overview',
            'techniques','methodologies','methodology','concepts','topics',
            'tools','strategies','patterns','styles','principles')
     OR lower(s.skill_name) IN (
            'cloud','cloud computing','api','apis','database','databases',
            'software','software development','software architecture','software engineering',
            'programming','coding','development','engineering',
            'automation','testing','test','architecture','design','integration',
            'monitoring','performance','security','frontend','front end','backend',
            'back end','full stack','fullstack','web','mobile','data','analytics',
            'devops','infrastructure','networking','operating systems','computer science')
        -- Rule B
     OR (
            NOT EXISTS (SELECT 1 FROM recruitment_skills rs WHERE rs.skill_id = s.skill_id)
            AND (
                 EXISTS (SELECT 1 FROM skill_nodes c
                           JOIN skill_nodes p ON p.node_id = c.parent_node
                          WHERE p.skill_id = s.skill_id)
              OR NOT EXISTS (SELECT 1 FROM skill_nodes n WHERE n.skill_id = s.skill_id)
            )
        )
  );

-- ---------------------------------------------------------------------------
-- Verify. Every career should hold a handful of HIGH rows, none of them
-- without market evidence, and they should read like that career's stack.
-- ---------------------------------------------------------------------------
SELECT r.career_name,
       count(*) FILTER (WHERE crs.importance_level = 'HIGH') AS high,
       count(*) FILTER (WHERE crs.importance_level = 'HIGH' AND p.n IS NULL) AS high_without_evidence
FROM career_required_skills crs
JOIN career_roles r ON r.career_id = crs.career_id
LEFT JOIN (SELECT skill_id, count(*) n FROM recruitment_skills GROUP BY 1) p
       ON p.skill_id = crs.skill_id
GROUP BY 1 ORDER BY 1;

-- ============================================================================
-- UNDO
-- ============================================================================
--   UPDATE career_required_skills crs SET importance_level = u.old_importance
--   FROM career_core_regrade_undo u WHERE u.skill_required_id = crs.skill_required_id;
--
--   DROP TABLE career_core_regrade_undo;
-- ============================================================================
