-- ============================================================================
-- F4b — career_required_skills, rebuilt against the job market
--
-- Run after 2026-08-06_option_skill_relink.sql, after every reseed.
-- Run inside a single transaction: psql -1 -f this_file.sql
--
-- SCOPE: BACKFILL ONLY. Part 2 is not the owner of these grades.
--
-- `skill_trends` is rebuilt nightly from the posting corpus, so a grade written
-- by hand is true the day it runs and drifts from the next crawl onwards — which
-- is exactly how the catalog reached the state described below. Ownership of the
-- grading therefore sits in CareerSkillMarketGrader, which runs at the end of
-- SkillExtractionServiceImpl.extractAndRebuildSkillTrends() against the trends it
-- has just written, using the same thresholds. Part 2 exists so an existing
-- database is correct now instead of after the next nightly run.
--
-- Part 1 has no such counterpart and was a manual step when this ran.
--
-- CORRECTION (2026-08-06, later the same day). This header used to claim that
-- attributing a skill to a career "cannot be automated" because `recruitments`
-- carries a seniority but no career. That conclusion was wrong, and it was wrong
-- because only the table's columns were looked at:
--
--   * `recruitments.recruitment_infos->>'title'` exists on all 913 postings, and
--     a keyword pass over it classifies 494 of them (54%) into a career —
--     192 of those Backend.
--   * SkillExtractionServiceImpl already knows the skills of EACH INDIVIDUAL
--     posting: `aiServiceClient.extractSkills(descriptions)` returns one list per
--     description. That per-posting detail is then aggregated by date into
--     skill_trends and discarded.
--
-- Skill × career demand is therefore derivable from data the pipeline already
-- computes and throws away, not a thing requiring hand-seeded CSV. The reason it
-- has not been done is cost, not possibility: nothing persists the per-posting
-- skills, so a backfill means re-running the extraction over all 913
-- descriptions through the AI service.
--
-- Left standing as the record of what actually ran. Do not cite it as evidence
-- that a CSV is the only option.
--
-- WHY
-- ---
-- Two faults, and they compound.
--
--   1. The catalog is the roadmap tree flattened. Backend "requires" 1466
--      skills, among them `$match` (a MongoDB operator), `path module` (Node),
--      `Kyo` (a Scala effects library) and an entry literally named `Backend`.
--
--   2. `importance_level` is wrong where it matters most. Measured against the
--      posting corpus, Backend files Python (193 postings), Java (158) and
--      PostgreSQL (53) as LOW, while Tensorflow (2) and Ruby on Rails (0) sit
--      at HIGH.
--
-- Together they broke GitHub import outright. PortfolioAiAnalyzer hands the
-- model the whole catalog and asks it to name the skills a repository shows;
-- given 1466 mostly-irrelevant candidates it returned an empty list for three
-- consecutive Spring Boot repositories. Empty list -> no evidence rows -> no
-- proficiency promotion -> no node ever completed. The student synced three
-- projects and the roadmap did not move.
--
-- Filtering the catalog by the existing importance would have made that worse,
-- not better: HIGH+AVG for Backend excludes Java, Spring Boot, PostgreSQL,
-- Hibernate and Maven — precisely the skills those repositories demonstrate.
-- So the importance has to be recomputed before it can be trusted to filter.
--
-- WHAT IT DOES
-- ------------
-- Part 1 adds the skills the postings name but the tree never modelled, from
-- data/v2/career_skills_from_market.csv (74 rows, counted over the crawled
-- corpus). `Spring Boot` is the case that proves the need: 35 postings name it
-- and no career catalog contains it, because the tree only ever had the node
-- `Spring (Spring Boot)`.
--
-- Part 2 recomputes importance from skill_trends, per career, scaled to that
-- career's own leading skill rather than to a global constant — 176 postings
-- makes SQL a headline skill for Backend and an also-ran for Game Developer.
--
-- WHAT IT DOES NOT DO
-- -------------------
-- Nothing is deleted. A skill with no posting evidence KEEPS its seeded
-- importance instead of being demoted, because absence of evidence here means
-- the crawler never saw the term, not that employers do not want it — `bcrypt`
-- and `OWASP Risks` are real backend topics that no job advert spells out.
-- Demotion is reserved for skills the market did speak about and ranked low.
--
-- The CSV's non-technical rows (Teamwork, Leadership, KPI, WordPress) are
-- loaded as measured rather than hand-dropped. They rank where their posting
-- counts put them, which is low, and the prompt catalog is ordered by that
-- ranking — so they sink on the evidence instead of on my opinion.
-- ============================================================================

-- One row per (skill_required_id, action), NOT per skill_required_id.
--
-- The first version keyed on skill_required_id alone, and it silently broke the
-- migration: Part 1 recorded its 74 inserts, then Part 2's `ON CONFLICT DO
-- NOTHING` hit those same ids and skipped them — so the 74 rows Part 1 had just
-- created were never re-graded and kept their raw CSV importance. 57 of them were
-- wrong, and nothing said so. A row can legitimately be both inserted and
-- re-graded by one run; the key has to allow that.
CREATE TABLE IF NOT EXISTS career_skill_market_undo (
    skill_required_id UUID,
    career_id         UUID,
    skill_id          UUID,
    old_importance    VARCHAR(20),
    new_importance    VARCHAR(20),
    action            VARCHAR(10),
    applied_at        TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (skill_required_id, action)
);

-- ----------------------------------------------------------------------------
-- Part 1 — load the market-observed skills
--
-- Keyed on career_name, not on the career UUIDs, so the file survives a reseed
-- that mints new ids.
-- ----------------------------------------------------------------------------

CREATE TEMP TABLE market_skills (
    skill_name  VARCHAR(255),
    career_slug VARCHAR(40),
    importance  VARCHAR(20),
    hits        INT
) ON COMMIT DROP;

INSERT INTO market_skills (skill_name, career_slug, importance, hits) VALUES
('Spring Boot','backend','HIGH',35),
('Agile','backend','HIGH',26),
('Spring','backend','HIGH',23),
('Scrum','backend','AVG',6),
('Teamwork','backend','AVG',6),
('FastAPI','backend','AVG',5),
('E-commerce','backend','AVG',5),
('Database Management','backend','AVG',5),
('WordPress','backend','AVG',4),
('Cloud Services','backend','AVG',3),
('Software Architecture','backend','AVG',3),
('LLM','data-science','HIGH',39),
('Data Engineering','data-science','HIGH',20),
('MLOps','data-science','HIGH',20),
('Computer Vision','data-science','HIGH',17),
('ETL','data-science','HIGH',17),
('Generative AI','data-science','HIGH',15),
('Big Data','data-science','HIGH',14),
('Agile','data-science','AVG',12),
('NLP','data-science','AVG',10),
('Data Warehousing','data-science','AVG',9),
('Data Governance','data-science','AVG',6),
('Software Engineering','data-science','AVG',6),
('Data Modeling','data-science','AVG',6),
('FastAPI','data-science','AVG',6),
('Software Architecture','data-science','AVG',6),
('Digital Transformation','data-science','AVG',5),
('Leadership','data-science','AVG',5),
('C++','data-science','AVG',5),
('Business Analysis','data-science','AVG',5),
('Data Quality','data-science','AVG',5),
('Technology','data-science','AVG',4),
('OCR','data-science','AVG',4),
('Data Visualization','data-science','AVG',4),
('eCommerce','data-science','LOW',3),
('AI Tools','data-science','LOW',3),
('Solution Architecture','data-science','LOW',3),
('Data Processing','data-science','LOW',3),
('Product Management','data-science','LOW',3),
('Databricks','data-science','LOW',3),
('DataOps','data-science','LOW',3),
('KPI','data-science','LOW',3),
('Infrastructure','devops','HIGH',11),
('DevSecOps','devops','HIGH',8),
('Agile','devops','AVG',6),
('Firewall','devops','AVG',3),
('Virtualization','devops','AVG',3),
('Angular','frontend','HIGH',6),
('Redux','frontend','HIGH',5),
('Agile','frontend','HIGH',4),
('UI/UX','frontend','HIGH',3),
('Agile','full-stack','HIGH',14),
('Angular','full-stack','HIGH',13),
('Spring','full-stack','HIGH',5),
('Spring Boot','full-stack','AVG',4),
('C++','game-developer','HIGH',5),
('Agile','qa','HIGH',30),
('Software Testing','qa','HIGH',14),
('Jira','qa','HIGH',9),
('Automation Testing','qa','HIGH',8),
('Scrum','qa','AVG',5),
('Mobile Testing','qa','AVG',5),
('Test Automation','qa','AVG',4),
('Performance Testing','qa','AVG',4),
('Leadership','qa','AVG',4),
('Agile Development','qa','AVG',3),
('AI Tools','qa','AVG',3),
('Web Applications','qa','AVG',3),
('System Architecture','software-architect','HIGH',5),
('Solution Architecture','software-architect','HIGH',5),
('Agile','software-architect','HIGH',4),
('Enterprise Architecture','software-architect','HIGH',3),
('Spring Boot','software-architect','HIGH',3),
('Software Architecture','software-architect','HIGH',3);

CREATE TEMP TABLE slug_to_career (slug VARCHAR(40), career_name VARCHAR(255)) ON COMMIT DROP;
INSERT INTO slug_to_career VALUES
('backend','Backend'), ('data-science','Data Science'), ('devops','DevOps'),
('frontend','Frontend'), ('full-stack','Full Stack'), ('game-developer','Game Developer'),
('qa','QA'), ('software-architect','Software Architect');

-- A market-named skill that has no catalog row yet is a real gap, so mint it.
-- Case-insensitive on purpose: the CSV says "eCommerce" where a seeded row may
-- say "ECommerce", and two rows for one skill would double-count it downstream.
INSERT INTO skills (skill_name, category)
SELECT DISTINCT m.skill_name, 'market'
FROM market_skills m
WHERE NOT EXISTS (SELECT 1 FROM skills s WHERE lower(s.skill_name) = lower(m.skill_name));

-- Attach them to their careers. ON CONFLICT keeps whatever the catalog already
-- had; Part 2 is where an existing row's importance gets revisited.
WITH resolved AS (
    SELECT DISTINCT ON (cr.career_id, s.skill_id)
           cr.career_id, s.skill_id, m.importance
    FROM market_skills m
    JOIN slug_to_career sc ON sc.slug = m.career_slug
    JOIN career_roles cr ON cr.career_name = sc.career_name
    JOIN skills s ON lower(s.skill_name) = lower(m.skill_name)
    ORDER BY cr.career_id, s.skill_id, m.hits DESC
), inserted AS (
    INSERT INTO career_required_skills (career_id, skill_id, importance_level)
    SELECT career_id, skill_id, importance FROM resolved
    ON CONFLICT (career_id, skill_id) DO NOTHING
    RETURNING skill_required_id, career_id, skill_id, importance_level
)
INSERT INTO career_skill_market_undo (skill_required_id, career_id, skill_id, old_importance, new_importance, action)
SELECT skill_required_id, career_id, skill_id, NULL, importance_level, 'INSERT' FROM inserted
ON CONFLICT (skill_required_id, action) DO NOTHING;

-- ----------------------------------------------------------------------------
-- Part 2 — recompute importance from the posting corpus
--
-- Scaled within the career. Absolute counts cannot be compared across careers:
-- Backend's leading skill is named by 193 postings and QA's by 30, so one fixed
-- threshold would file most of QA as LOW for reasons that have nothing to do
-- with QA.
-- ----------------------------------------------------------------------------

WITH demand AS (
    SELECT crs.career_id, crs.skill_id, sum(coalesce(t.jobs_needed, 0)) AS jobs
    FROM career_required_skills crs
    JOIN skill_trends t ON t.skill_id = crs.skill_id
    GROUP BY crs.career_id, crs.skill_id
    HAVING sum(coalesce(t.jobs_needed, 0)) > 0
), leader AS (
    SELECT career_id, max(jobs) AS top_jobs FROM demand GROUP BY career_id
), graded AS (
    SELECT d.career_id, d.skill_id,
           CASE WHEN d.jobs >= 0.25 * l.top_jobs THEN 'HIGH'
                WHEN d.jobs >= 0.05 * l.top_jobs THEN 'AVG'
                ELSE 'LOW' END AS new_importance
    FROM demand d JOIN leader l ON l.career_id = d.career_id
)
INSERT INTO career_skill_market_undo (skill_required_id, career_id, skill_id, old_importance, new_importance, action)
SELECT crs.skill_required_id, crs.career_id, crs.skill_id, crs.importance_level, g.new_importance, 'REGRADE'
FROM career_required_skills crs
JOIN graded g ON g.career_id = crs.career_id AND g.skill_id = crs.skill_id
WHERE crs.importance_level IS DISTINCT FROM g.new_importance
ON CONFLICT (skill_required_id, action) DO NOTHING;

UPDATE career_required_skills crs
SET importance_level = u.new_importance
FROM career_skill_market_undo u
WHERE u.skill_required_id = crs.skill_required_id
  AND u.action = 'REGRADE'
  AND crs.importance_level IS DISTINCT FROM u.new_importance;

-- ----------------------------------------------------------------------------
-- Verify
--
--   -- Java, Python, PostgreSQL must no longer be LOW for Backend:
--   SELECT s.skill_name, crs.importance_level
--   FROM career_required_skills crs
--   JOIN skills s ON s.skill_id = crs.skill_id
--   JOIN career_roles c ON c.career_id = crs.career_id
--   WHERE c.career_name = 'Backend'
--     AND s.skill_name IN ('Java','Python','PostgreSQL','Spring Boot','Tensorflow');
--
--   -- Nothing lost: the catalog only ever grows here.
--   SELECT (SELECT count(*) FROM f4b_before_snapshot) AS before,
--          (SELECT count(*) FROM career_required_skills) AS after;
--
-- Undo:
--   UPDATE career_required_skills crs SET importance_level = u.old_importance
--   FROM career_skill_market_undo u
--   WHERE u.skill_required_id = crs.skill_required_id AND u.action = 'REGRADE';
--   DELETE FROM career_required_skills crs
--   USING career_skill_market_undo u
--   WHERE u.skill_required_id = crs.skill_required_id AND u.action = 'INSERT';
-- ----------------------------------------------------------------------------
