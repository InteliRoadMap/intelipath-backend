-- ============================================================================
-- One skill, one row: merging the catalog's forks
--
-- WHY
-- ---
-- Until now the only thing deciding whether two names meant the same skill was
-- SkillRepository.findOneBySkillNameIgnoreCase - an exact match modulo case.
-- That is a string comparison, not an identity function, and three separate
-- writers (the seed CSVs, LLM skill extraction, GitHub evidence) each fed it
-- their own dialect. The catalog forked accordingly. Measured before this ran:
--
--   Fast API        0 postings   beside  FastAPI         12
--   React.js        0            beside  React           69
--   Micro-service   2            beside  Microservices   86
--   Elastic Search  0            beside  Elasticsearch    4
--   MS SQL          0            beside  MSSQL            2
--
-- Every fork splits one skill's evidence in two. A student holding FastAPI is
-- matched against whichever row their spelling landed on and measured against
-- the other. 45 career_required_skills rows sat at HIGH with zero postings -
-- and HIGH is the readiness denominator, so a Frontend student was divided by
-- 26 when only 9 of those rows named something an employer had ever asked for.
--
-- WHERE THE PAIRS COME FROM
-- -------------------------
-- SkillNameCanonicalizer.matchKey, run over the live catalog before this file
-- was written. The pair list below is that output, not a hand-typed guess, so
-- the migration and the running code cannot disagree about what is a fork.
--
-- Two false merges were caught by doing it in that order and are NOT in the
-- list: HTTPS -> HTTP (the s of an acronym is not a plural) and Slices ->
-- $slice (a MongoDB operator is not Go's slices). Both are now pinned by tests.
--
-- Eight further pairs needed judgement rather than a rule - GCP, IaC, REST, SRE
-- and three "... Architecture" headings - and were reviewed one at a time
-- against their posting counts before being added to the canonicaliser's alias
-- table. They appear here because they are in that table, not as extras.
--
-- SCALE
-- -----
-- 91 fork rows removed, carrying 16 postings between them. The overwhelming
-- majority differ only by a plural or a markdown backtick (Queues -> Queue,
-- `pg_ctl` -> pg_ctl), which is why so little evidence moves.
--
-- WHAT SURVIVES
-- -------------
-- Every posting link, every roadmap node, every student's proficiency and
-- verification. Rows are re-pointed at the canonical skill, and where that
-- would collide with a row that already exists, the STRONGER of the two is
-- kept: highest importance, highest proficiency, any verifier.
--
-- REVERSIBLE
-- ----------
-- skill_merge_undo records every (fork -> canonical) decision with the fork's
-- original name and id. The UNDO block at the foot restores the rows; it cannot
-- restore which reference used to point where, which is why the strongest row
-- is the one kept rather than whichever came first.
--
-- RUN AS ONE TRANSACTION:
--   psql -1 -U intelipath -d intelipath -f 2026-08-06_skill_catalog_merge.sql
-- ============================================================================

CREATE TABLE IF NOT EXISTS skill_merge_undo (
    fork_id        uuid PRIMARY KEY,
    fork_name      varchar(255) NOT NULL,
    canonical_id   uuid NOT NULL,
    canonical_name varchar(255) NOT NULL,
    merged_at      timestamp NOT NULL DEFAULT now()
);

-- The pairs, as produced by SkillNameCanonicalizer.matchKey.
CREATE TEMP TABLE merge_map ON COMMIT DROP AS
WITH pairs(fork_name, canonical_name) AS (VALUES
    ('Aggregations', 'Aggregation'),
    ('Animations', 'Animation'),
    ('Caches', 'Cache'),
    ('Collections', 'Collection'),
    ('Colors', 'Color'),
    ('Communications', 'Communication'),
    ('Conditionals', 'Conditional'),
    ('Containers & Orchestration', 'Container Orchestration'),
    ('`context` Package', 'context Package'),
    ('CSS', 'CSS3'),
    ('Cursors', 'Cursor'),
    ('Dashboards', 'Dashboard'),
    ('Data Pipelines', 'Data Pipeline'),
    ('Databases', 'Database'),
    ('Documenting with `rustdoc`', 'Documenting with rustdoc'),
    ('Domain names', 'Domain Name'),
    ('eCommerce', 'E-commerce'),
    ('Ecosystems', 'Ecosystem'),
    ('Elastic Search', 'Elasticsearch'),
    ('Embeddings', 'Embedding'),
    ('`error` interface', 'error interface'),
    ('Event-Driven Architecture (EDA)', 'Event-Driven'),
    ('Fast API', 'FastAPI'),
    ('Firewalls', 'Firewall'),
    ('Front-end Development', 'Frontend development'),
    ('Functions', 'Function'),
    ('Google Cloud Platform', 'GCP'),
    ('Google Cloud Platform (GCP)', 'GCP'),
    ('Graphs', 'Graph'),
    ('HashMaps', 'Hashmap'),
    ('HTML', 'HTML5'),
    ('Import / Export Using `COPY`', 'Import / Export Using COPY'),
    ('Infrastructure as Code (IaC)', 'Infrastructure as Code'),
    ('Infrastructure as Code - IaC', 'Infrastructure as Code'),
    ('Integrations', 'Integration'),
    ('Iterators', 'Iterator'),
    ('JOINs', 'join'),
    ('Layouts', 'Layout'),
    ('Life Cycles', 'lifecycle'),
    ('Lists', 'List'),
    ('Logs Management', 'Log Management'),
    ('Logging Frameworks', 'Logging Framework'),
    ('Mappings', 'Mapping'),
    ('Message Brokers', 'Message Broker'),
    ('Micro-service', 'Microservices'),
    ('Microservices Architecture', 'Microservices'),
    ('MS SQL', 'MSSQL'),
    ('Networks', 'Network'),
    ('NextJS', 'Next.js'),
    ('Object Oriented Programming', 'Object-Oriented Programming'),
    ('Open API Specs', 'Open API Spec'),
    ('Operating Systems', 'Operating System'),
    ('ORMs', 'ORM'),
    ('Outputs', 'output'),
    ('Packages', 'Package'),
    ('`panic` and `recover`', 'panic and recover'),
    ('Pre-Sales', 'Presales'),
    ('Programming Languages', 'Programming Language'),
    ('Providers', 'provider'),
    ('Queues', 'Queue'),
    ('RAGs', 'RAG'),
    ('Ranges', 'Range'),
    ('React.js', 'React'),
    ('Reinforcements Learning', 'Reinforcement Learning'),
    ('RESTful API', 'REST'),
    ('Secrets Management', 'Secret Management'),
    ('Sockets', 'Socket'),
    ('Software Architecture Fundamentals', 'Software Architecture'),
    ('Software Development Life Cycle', 'Software Development Lifecycle'),
    ('Site Reliability Engineering (SRE)', 'SRE'),
    ('Stacks', 'Stack'),
    ('Standards', 'Standard'),
    ('Streams', 'Stream'),
    ('Structured Outputs', 'Structured Output'),
    ('Styles', 'style'),
    ('`sync` Package', 'sync Package'),
    ('Tables', 'Table'),
    ('Templates', 'Template'),
    ('`testing` package basics', 'testing package basics'),
    ('Trees', 'Tree'),
    ('Tuples', 'Tuple'),
    ('Using `pg_ctl`', 'Using pg_ctl'),
    ('Using `pg_upgrade`', 'Using pg_upgrade'),
    ('Using `systemd`', 'Using systemd'),
    ('Validations', 'Validation'),
    ('Variable Scopes', 'Variable Scope'),
    ('Vectors', 'Vector'),
    ('Vector Databases', 'Vector Database'),
    ('Version Control Systems', 'Version Control System'),
    ('Vue.js', 'Vue'),
    ('WebSockets', 'WebSocket')
)
SELECT f.skill_id   AS fork_id,
       f.skill_name AS fork_name,
       c.skill_id   AS canonical_id,
       c.skill_name AS canonical_name
FROM pairs p
JOIN skills f ON f.skill_name = p.fork_name
JOIN skills c ON c.skill_name = p.canonical_name
WHERE f.skill_id <> c.skill_id;

INSERT INTO skill_merge_undo (fork_id, fork_name, canonical_id, canonical_name)
SELECT fork_id, fork_name, canonical_id, canonical_name FROM merge_map
ON CONFLICT (fork_id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- career_required_skills - unique (career_id, skill_id)
--
-- Grade first, then delete. If a career held Fast API at HIGH and FastAPI at
-- LOW, deleting the fork before grading would silently demote the skill, so the
-- canonical row takes the strongest grade of the group before anything goes.
-- ---------------------------------------------------------------------------
WITH strongest AS (
    SELECT c.career_id, m.canonical_id,
           min(CASE upper(coalesce(f.importance_level, 'LOW'))
                   WHEN 'HIGH' THEN 0 WHEN 'AVG' THEN 1 ELSE 2 END) AS best
    FROM merge_map m
    JOIN career_required_skills f ON f.skill_id = m.fork_id
    JOIN career_required_skills c ON c.skill_id = m.canonical_id
                                 AND c.career_id = f.career_id
    GROUP BY c.career_id, m.canonical_id
)
UPDATE career_required_skills crs
SET importance_level = CASE s.best WHEN 0 THEN 'HIGH' WHEN 1 THEN 'AVG' ELSE 'LOW' END
FROM strongest s
WHERE crs.career_id = s.career_id
  AND crs.skill_id = s.canonical_id
  AND CASE upper(coalesce(crs.importance_level, 'LOW'))
          WHEN 'HIGH' THEN 0 WHEN 'AVG' THEN 1 ELSE 2 END > s.best;

DELETE FROM career_required_skills f
USING merge_map m, career_required_skills c
WHERE f.skill_id = m.fork_id
  AND c.skill_id = m.canonical_id
  AND c.career_id = f.career_id;

UPDATE career_required_skills crs
SET skill_id = m.canonical_id
FROM merge_map m
WHERE crs.skill_id = m.fork_id;

-- ---------------------------------------------------------------------------
-- student_skills - unique (user_id, skill_id)
--
-- Same shape, two things to preserve rather than one: the higher proficiency
-- and any verifier at all. A student who had FastAPI self-declared and Fast API
-- verified by GitHub must come out of this verified.
-- ---------------------------------------------------------------------------
WITH strongest AS (
    SELECT c.user_id, m.canonical_id,
           max(coalesce(f.proficiency, 0)) AS best_proficiency,
           max(f.verified_by)              AS a_verifier
    FROM merge_map m
    JOIN student_skills f ON f.skill_id = m.fork_id
    JOIN student_skills c ON c.skill_id = m.canonical_id AND c.user_id = f.user_id
    GROUP BY c.user_id, m.canonical_id
)
UPDATE student_skills ss
SET proficiency = greatest(coalesce(ss.proficiency, 0), s.best_proficiency),
    verified_by = coalesce(ss.verified_by, s.a_verifier)
FROM strongest s
WHERE ss.user_id = s.user_id
  AND ss.skill_id = s.canonical_id;

DELETE FROM student_skills f
USING merge_map m, student_skills c
WHERE f.skill_id = m.fork_id
  AND c.skill_id = m.canonical_id
  AND c.user_id = f.user_id;

UPDATE student_skills ss
SET skill_id = m.canonical_id
FROM merge_map m
WHERE ss.skill_id = m.fork_id;

-- ---------------------------------------------------------------------------
-- recruitment_skills - unique (recruitment_id, skill_id)
--
-- A link carries no grade, so the duplicate is simply dropped. This is the only
-- count that falls, and only where one posting naming both "Fast API" and
-- "FastAPI" was counted twice - which is the correction, not a loss.
-- ---------------------------------------------------------------------------
DELETE FROM recruitment_skills f
USING merge_map m, recruitment_skills c
WHERE f.skill_id = m.fork_id
  AND c.skill_id = m.canonical_id
  AND c.recruitment_id = f.recruitment_id;

UPDATE recruitment_skills rs
SET skill_id = m.canonical_id
FROM merge_map m
WHERE rs.skill_id = m.fork_id;

-- ---------------------------------------------------------------------------
-- No unique key to defend on these three.
-- ---------------------------------------------------------------------------
UPDATE skill_nodes n SET skill_id = m.canonical_id FROM merge_map m WHERE n.skill_id = m.fork_id;
UPDATE skill_trends t SET skill_id = m.canonical_id FROM merge_map m WHERE t.skill_id = m.fork_id;
UPDATE fpt_subject_skills s SET skill_id = m.canonical_id FROM merge_map m WHERE s.skill_id = m.fork_id;

-- ---------------------------------------------------------------------------
-- student_skill_evidence stores the skill as free text, not as a foreign key.
-- SkillNameCanonicalizer resolves the old spellings anyway, so this is tidying
-- rather than repair - but leaving "Fast API" in the evidence log after the
-- catalog stopped having such a row makes the audit screen contradict itself.
-- ---------------------------------------------------------------------------
UPDATE student_skill_evidence e
SET skill_name = m.canonical_name
FROM merge_map m
WHERE e.skill_name = m.fork_name;

-- The forks themselves. Nothing references them by now.
DELETE FROM skills s USING merge_map m WHERE s.skill_id = m.fork_id;

-- ---------------------------------------------------------------------------
-- Verify before committing. The first check must return 0.
-- ---------------------------------------------------------------------------
SELECT 'orphaned references' AS check_name, count(*) AS n FROM (
    SELECT skill_id FROM career_required_skills
    UNION ALL SELECT skill_id FROM recruitment_skills
    UNION ALL SELECT skill_id FROM skill_trends
    UNION ALL SELECT skill_id FROM student_skills
    UNION ALL SELECT skill_id FROM skill_nodes WHERE skill_id IS NOT NULL
    UNION ALL SELECT skill_id FROM fpt_subject_skills WHERE skill_id IS NOT NULL
) r WHERE NOT EXISTS (SELECT 1 FROM skills s WHERE s.skill_id = r.skill_id);

SELECT 'rows merged' AS check_name, count(*) AS n FROM skill_merge_undo;

-- ============================================================================
-- UNDO
-- ============================================================================
-- Restores the fork rows. It cannot un-merge the references: they now point at
-- the canonical skill and nothing records which of them used to point where,
-- because erasing that distinction is the entire purpose of this migration.
-- Recovering it means restoring from a backup taken before the run.
--
--   INSERT INTO skills (skill_id, skill_name)
--   SELECT fork_id, fork_name FROM skill_merge_undo
--   ON CONFLICT (skill_id) DO NOTHING;
--
--   UPDATE student_skill_evidence e SET skill_name = u.fork_name
--   FROM skill_merge_undo u WHERE e.skill_name = u.canonical_name;
--
--   DROP TABLE skill_merge_undo;
-- ============================================================================
