-- ============================================================================
-- Sub-topics are not career skills
--
-- WHY
-- ---
-- `career_required_skills` answers one question: what does this job need? It had
-- 46 rows answering a different one. `$lookup` is not a skill an employer hires
-- for — it is one operator inside MongoDB, which is inside NoSQL, which is inside
-- ASP.NET Core. It sits at depth 6 of the roadmap and was registered next to Java
-- and PostgreSQL as an equal.
--
-- They come from the roadmap.sh scrape: every node title became a skill, so
-- MongoDB query operators ($match, $group, $unwind), SCSS at-rules (@if), Angular
-- decorators (@Input & @Output), Spring annotations (@SpringBootTest), Go stdlib
-- packages and CLI flags (--watch) all landed in the catalog.
--
-- Ten of them still carry markdown backticks from that scrape and render as
-- `sync` Package on screen. That is a display bug regardless of the rest.
--
-- WHAT SURVIVES
-- -------------
-- The roadmap nodes. Every one of these stays exactly where it is in the tree,
-- so the student still learns MongoDB's operators and Go's sync package. Only the
-- claim "this is a professional skill for your career" is withdrawn.
--
-- The skills rows survive too. Nothing is deleted from `skills` — a node points
-- at its skill, and deleting the row would tear content out of the tree.
--
-- TWO SIGNALS MUST AGREE
-- ----------------------
-- Removal requires BOTH:
--   1. the name is written in sub-topic notation (does not start with a letter or
--      digit, or still contains markdown backticks), AND
--   2. no job posting in the whole market dataset mentions it.
--
-- Either test alone is wrong. On (1) alone this would delete `.NET`, which starts
-- with a dot and is a real skill with 54 postings behind it. On (2) alone it would
-- delete every genuine skill the scraper has simply not seen demand for yet.
--
-- Demanding both means the rule protects itself: a sub-topic that turns out to be
-- something employers ask for by name is kept, without anyone having to maintain
-- an exceptions list. That is the same mistake as hand-copying a CSV into SQL, and
-- it is avoided here on purpose.
-- ============================================================================

CREATE TABLE IF NOT EXISTS career_skill_cleanup_undo (
    skill_required_id UUID PRIMARY KEY,
    career_id         UUID,
    skill_id          UUID,
    importance_level  VARCHAR(20),
    skill_name        TEXT,
    applied_at        TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS skill_name_backtick_undo (
    entity_type VARCHAR(20),
    entity_id   UUID,
    old_name    TEXT,
    new_name    TEXT,
    applied_at  TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (entity_type, entity_id)
);

-- ---------------------------------------------------------------------------
-- Part 1 — withdraw the sub-topics from the career catalogs
-- ---------------------------------------------------------------------------

INSERT INTO career_skill_cleanup_undo (skill_required_id, career_id, skill_id, importance_level, skill_name)
SELECT crs.skill_required_id, crs.career_id, crs.skill_id, crs.importance_level, s.skill_name
FROM career_required_skills crs
JOIN skills s ON s.skill_id = crs.skill_id
WHERE (s.skill_name ~ '^[^a-zA-Z0-9]' OR s.skill_name LIKE '%`%')
  AND NOT EXISTS (
      SELECT 1 FROM skill_trends t
      WHERE t.skill_id = s.skill_id AND coalesce(t.jobs_needed, 0) > 0
  )
ON CONFLICT (skill_required_id) DO NOTHING;

DELETE FROM career_required_skills crs
USING career_skill_cleanup_undo u
WHERE u.skill_required_id = crs.skill_required_id;

-- ---------------------------------------------------------------------------
-- Part 2 — strip the markdown backticks
--
-- Applied to every backticked name, including ones Part 1 left in a career
-- catalog: a name that renders wrong renders wrong either way. Verified against
-- collisions first — stripping produced no duplicate of an existing name.
-- ---------------------------------------------------------------------------

INSERT INTO skill_name_backtick_undo (entity_type, entity_id, old_name, new_name)
SELECT 'SKILL', skill_id, skill_name, replace(skill_name, '`', '')
FROM skills WHERE skill_name LIKE '%`%'
ON CONFLICT (entity_type, entity_id) DO NOTHING;

UPDATE skills s SET skill_name = u.new_name
FROM skill_name_backtick_undo u
WHERE u.entity_type = 'SKILL' AND u.entity_id = s.skill_id AND s.skill_name <> u.new_name;

INSERT INTO skill_name_backtick_undo (entity_type, entity_id, old_name, new_name)
SELECT 'NODE', node_id, node_name, replace(node_name, '`', '')
FROM skill_nodes WHERE node_name LIKE '%`%'
ON CONFLICT (entity_type, entity_id) DO NOTHING;

UPDATE skill_nodes n SET node_name = u.new_name
FROM skill_name_backtick_undo u
WHERE u.entity_type = 'NODE' AND u.entity_id = n.node_id AND n.node_name <> u.new_name;

-- Verify:
--   -- nothing written in sub-topic notation is claimed as a career skill any more,
--   -- except names the market actually asks for:
--   SELECT s.skill_name, coalesce(sum(t.jobs_needed),0) AS jobs
--   FROM career_required_skills crs
--   JOIN skills s ON s.skill_id = crs.skill_id
--   LEFT JOIN skill_trends t ON t.skill_id = s.skill_id
--   WHERE s.skill_name ~ '^[^a-zA-Z0-9]' OR s.skill_name LIKE '%`%'
--   GROUP BY s.skill_name;   -- expect only .NET
--
--   -- the roadmap kept every node:
--   SELECT count(*) FROM skill_nodes WHERE node_name LIKE '$%';  -- unchanged
--
-- Undo:
--   INSERT INTO career_required_skills (skill_required_id, career_id, skill_id, importance_level)
--   SELECT skill_required_id, career_id, skill_id, importance_level FROM career_skill_cleanup_undo;
--   UPDATE skills s SET skill_name = u.old_name FROM skill_name_backtick_undo u
--     WHERE u.entity_type='SKILL' AND u.entity_id = s.skill_id;
--   UPDATE skill_nodes n SET node_name = u.old_name FROM skill_name_backtick_undo u
--     WHERE u.entity_type='NODE' AND u.entity_id = n.node_id;
