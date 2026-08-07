-- ============================================================================
-- Roadmap spine order — Frontend / Backend
--
-- Run after 2026-08-05_roadmap_choice_zones.sql, after every reseed.
--
-- WHY
-- ---
-- Backend's first nine steps were API Design, ASP.NET Core, Backend Beginner,
-- Golang, Kotlin, Laravel, PostgreSQL DBA, Scala -- eight imported roadmaps in
-- ALPHABETICAL order. The real learning path started at position 10 (Internet)
-- and ran coherently to position 23. Frontend had the same fault: Design
-- System, Frontend, Frontend Beginner, TypeScript occupied 1-4 and the path
-- began at 6.
--
-- Two consequences, both of which a student sees:
--
--   1. `Internet` -- the first thing anyone learns -- was step 9, and
--      `Pick a Language` was step 11, so it rendered LOCKED behind eight steps
--      that have nothing to do with it. A locked group yields no options, and
--      with no options there is nothing for the choice zone to enclose and
--      nothing for the market rail to rank. Both features were built and both
--      drew blank for exactly this reason.
--
--   2. Those nine steps depend on the student not at all, so every student's
--      roadmap opened identically. The whole point of the choosers is that two
--      students diverge, and the divergence was buried below the fold.
--
-- The imported roadmaps are not junk -- Golang carries 123 nodes, ASP.NET Core
-- 157. They are SUBROADMAPS filed at the wrong depth. This puts each one under
-- the thing it is a specialisation of, where the "Go Deeper" rail already
-- expects to find it.
--
-- WHAT IT DOES NOT DO
-- -------------------
-- Nothing is deleted. Shadow copies go to DRAFT, which the FR2.3 publication
-- gate already understands: withheld from students, still there for an editor.
-- Every change is recorded in skill_node_zone_undo / skill_node_order_undo.
--
-- Full Stack is deliberately untouched. Its 42 roots carry subtree sizes of
-- 0-3: it is not mis-ordered, it is unwritten. Reordering empty nodes would
-- make it look finished without making it useful.
-- ============================================================================

CREATE TABLE IF NOT EXISTS skill_node_order_undo (
    node_id        UUID PRIMARY KEY,
    old_sort_order INT,
    new_sort_order INT,
    applied_at     TIMESTAMP DEFAULT NOW()
);

-- ---------------------------------------------------------------------------
-- 1. Reparent the imported roadmaps under what they specialise.
--
-- Resolved by (career, name) rather than by UUID: the seeder mints new ids on
-- every reseed, so ids from a previous run are worthless here.
-- ---------------------------------------------------------------------------
-- Held in a temp table rather than driven off skill_node_zone_undo, because in
-- that table a NULL new_parent means "parent unchanged" (the earlier migration
-- applies it through coalesce). Here one move genuinely targets NULL -- lifting
-- a node to the root -- and reusing the same encoding for both would orphan
-- every node the earlier migration only meant to re-status.
CREATE TEMP TABLE spine_moves (
    node_id    UUID PRIMARY KEY,
    old_parent UUID,
    new_parent UUID
) ON COMMIT DROP;

WITH target AS (
    SELECT sn.node_id, cr.career_name, sn.node_name, sn.parent_node,
           coalesce(sn.subtree_size, 0) AS sub
    FROM skill_nodes sn JOIN career_roles cr ON cr.career_id = sn.career_id
    WHERE cr.career_name IN ('Frontend', 'Backend')
), moves(career_name, child_name, parent_name) AS (
    VALUES
        -- Languages belong in the language chooser, not beside it.
        ('Backend',  'Golang',         'Pick a Language'),
        ('Backend',  'Kotlin',         'Pick a Language'),
        ('Backend',  'Scala',          'Pick a Language'),
        -- Frameworks belong under their language.
        ('Backend',  'ASP.NET Core',   'C#'),
        ('Backend',  'Laravel',        'PHP'),
        -- A database deep-dive belongs under databases.
        ('Backend',  'PostgreSQL DBA', 'Relational Databases'),
        -- TypeScript is typed JavaScript; it is not a peer of JavaScript.
        ('Frontend', 'TypeScript',     'JavaScript')
)
INSERT INTO spine_moves (node_id, old_parent, new_parent)
SELECT t.node_id, t.parent_node,
       (SELECT p.node_id FROM target p
         WHERE p.career_name = m.career_name AND p.node_name = m.parent_name
         ORDER BY p.sub DESC LIMIT 1)
FROM moves m
JOIN target t ON t.career_name = m.career_name AND t.node_name = m.child_name
             AND t.parent_node IS NULL;

DELETE FROM spine_moves WHERE new_parent IS NULL OR new_parent = node_id;

-- `AI Assisted Coding` (9 nodes) is the only content the Frontend shell node
-- holds that does not already exist elsewhere on the spine. Rescue it to the
-- root before the shell around it is withheld. This is the row whose target
-- really is NULL.
INSERT INTO spine_moves (node_id, old_parent, new_parent)
SELECT c.node_id, c.parent_node, NULL
FROM skill_nodes c
JOIN skill_nodes p ON p.node_id = c.parent_node AND p.parent_node IS NULL AND p.node_name = 'Frontend'
JOIN career_roles cr ON cr.career_id = c.career_id AND cr.career_name = 'Frontend'
WHERE c.node_name = 'AI Assisted Coding'
ON CONFLICT (node_id) DO NOTHING;

INSERT INTO skill_node_zone_undo (node_id, old_parent, new_parent, old_status, new_status)
SELECT m.node_id, m.old_parent, m.new_parent, sn.status, sn.status
FROM spine_moves m JOIN skill_nodes sn ON sn.node_id = m.node_id
ON CONFLICT (node_id) DO NOTHING;

UPDATE skill_nodes sn SET parent_node = m.new_parent
FROM spine_moves m WHERE m.node_id = sn.node_id;

-- ---------------------------------------------------------------------------
-- 1b. Link the moved languages to their skill row.
--
-- They arrived as imported roadmap roots and never carried a skill_id, so the
-- market rail would draw all three as "No posting data" -- which reads as "the
-- market does not want this" when it means "nobody joined the tables". `Golang`
-- is the roadmap's name and `Go` is the market's; without the alias the option
-- with 36 postings behind it would rank below Scala's 1.
-- ---------------------------------------------------------------------------
WITH alias(node_name, skill_name) AS (
    VALUES ('Golang', 'Go'), ('Kotlin', 'Kotlin'), ('Scala', 'Scala')
)
UPDATE skill_nodes sn SET skill_id = s.skill_id
FROM alias a
JOIN skills s ON s.skill_name = a.skill_name
JOIN career_roles cr ON cr.career_name = 'Backend'
WHERE sn.node_name = a.node_name AND sn.career_id = cr.career_id
  AND sn.skill_id IS NULL
  AND sn.parent_node = (SELECT node_id FROM skill_nodes
                        WHERE career_id = cr.career_id AND node_name = 'Pick a Language'
                          AND parent_node IS NULL LIMIT 1);

-- ---------------------------------------------------------------------------
-- 2. Withhold the shadow copies -- the shell node AND everything still under
--    it, so no child is left pointing at a parent students cannot see.
--
--    `Frontend` duplicated the spine it sits on: VCS Hosting, Learn a
--    Framework, SSR, SSG, Design Systems, Performance, Accessibility, PWAs --
--    every one of them a root that already exists with real content. Same
--    shape as `Frontend Beginner` (2) and `Backend Beginner` (4).
-- ---------------------------------------------------------------------------
WITH RECURSIVE shell AS (
    SELECT sn.node_id
    FROM skill_nodes sn JOIN career_roles cr ON cr.career_id = sn.career_id
    WHERE sn.parent_node IS NULL
      AND ((cr.career_name = 'Frontend' AND sn.node_name IN ('Frontend', 'Frontend Beginner'))
        OR (cr.career_name = 'Backend'  AND sn.node_name = 'Backend Beginner'))
    UNION
    SELECT c.node_id FROM shell s JOIN skill_nodes c ON c.parent_node = s.node_id
)
INSERT INTO skill_node_zone_undo (node_id, old_parent, new_parent, old_status, new_status)
SELECT sn.node_id, sn.parent_node, sn.parent_node, sn.status, 'DRAFT'
FROM shell s JOIN skill_nodes sn ON sn.node_id = s.node_id
WHERE sn.status IS DISTINCT FROM 'DRAFT'
ON CONFLICT (node_id) DO NOTHING;

UPDATE skill_nodes sn SET status = 'DRAFT'
FROM skill_node_zone_undo u
WHERE u.node_id = sn.node_id AND u.new_status = 'DRAFT' AND u.old_status IS DISTINCT FROM 'DRAFT';

-- ---------------------------------------------------------------------------
-- 3. Order the spine by what a student learns first, not by the alphabet.
--
--    Written out by hand rather than derived: there is no column that knows
--    caching comes after databases. The list IS the editorial decision, so it
--    is stated where it can be argued with.
-- ---------------------------------------------------------------------------
WITH spine(career_name, node_name, ord) AS (
    VALUES
        ('Backend', 'Internet',                          1),
        ('Backend', 'Pick a Language',                   2),
        ('Backend', 'Version Control',                   3),
        ('Backend', 'Relational Databases',              4),
        ('Backend', 'APIs',                              5),
        ('Backend', 'API Design',                        6),
        ('Backend', 'Caching',                           7),
        ('Backend', 'Password Hashing',                  8),
        ('Backend', 'Web Security',                      9),
        ('Backend', 'Testing',                          10),
        ('Backend', 'Web Servers',                      11),
        ('Backend', 'System Design',                    12),
        ('Backend', 'Event-Driven Architecture',        13),
        ('Backend', 'Scaling & Advanced Data',          14),
        ('Backend', 'Site Reliability & Observability', 15),

        ('Frontend', 'Internet',                         1),
        ('Frontend', 'HTML',                             2),
        ('Frontend', 'CSS',                              3),
        ('Frontend', 'JavaScript',                       4),
        ('Frontend', 'Version Control',                  5),
        ('Frontend', 'Package Managers',                 6),
        ('Frontend', 'Pick a Framework',                 7),
        ('Frontend', 'Writing CSS',                      8),
        ('Frontend', 'Build Tools',                      9),
        ('Frontend', 'Linters and Formatters',          10),
        ('Frontend', 'Testing',                         11),
        ('Frontend', 'Type Checkers',                   12),
        ('Frontend', 'Authentication',                  13),
        ('Frontend', 'Web Security',                    14),
        ('Frontend', 'Web Components',                  15),
        ('Frontend', 'Server-Side Rendering',           16),
        ('Frontend', 'GraphQL',                         17),
        ('Frontend', 'Static Site Generation',          18),
        ('Frontend', 'Progressive Web Apps',            19),
        ('Frontend', 'Web Performance',                 20),
        ('Frontend', 'Web Accessibility',               21),
        ('Frontend', 'Design System',                   22),
        ('Frontend', 'Cross-Platform Apps',             23),
        ('Frontend', 'AI Assisted Coding',              24)
)
INSERT INTO skill_node_order_undo (node_id, old_sort_order, new_sort_order)
SELECT sn.node_id, sn.sort_order, sp.ord
FROM spine sp
JOIN career_roles cr ON cr.career_name = sp.career_name
JOIN skill_nodes sn ON sn.career_id = cr.career_id AND sn.node_name = sp.node_name
                   AND sn.parent_node IS NULL AND sn.status IS DISTINCT FROM 'DRAFT'
WHERE sn.sort_order IS DISTINCT FROM sp.ord
ON CONFLICT (node_id) DO NOTHING;

UPDATE skill_nodes sn SET sort_order = u.new_sort_order
FROM skill_node_order_undo u WHERE u.node_id = sn.node_id;

-- The language chooser gained three options and already had two rows sharing
-- sort_order 7. Biggest track first -- the same "what am I signing up for"
-- reading the market rail shows as "N topics inside".
WITH ranked AS (
    SELECT c.node_id,
           row_number() OVER (ORDER BY coalesce(c.subtree_size, 0) DESC, c.node_name)::int AS ord
    FROM skill_nodes c
    JOIN skill_nodes p ON p.node_id = c.parent_node
    JOIN career_roles cr ON cr.career_id = c.career_id AND cr.career_name = 'Backend'
    WHERE p.node_name = 'Pick a Language' AND p.parent_node IS NULL
      AND c.status IS DISTINCT FROM 'DRAFT'
)
INSERT INTO skill_node_order_undo (node_id, old_sort_order, new_sort_order)
SELECT r.node_id, sn.sort_order, r.ord
FROM ranked r JOIN skill_nodes sn ON sn.node_id = r.node_id
WHERE sn.sort_order IS DISTINCT FROM r.ord
ON CONFLICT (node_id) DO NOTHING;

UPDATE skill_nodes sn SET sort_order = u.new_sort_order
FROM skill_node_order_undo u WHERE u.node_id = sn.node_id;

-- ============================================================================
-- Reparenting invalidates depth / root_node_id / subtree_size, and those are
-- what the student's node order is built from. Leaving them stale is how `$all`
-- and `$and` -- MongoDB operators four levels deep -- once became the first two
-- things the roadmap told a student to learn: the sort fell back to node_name,
-- and `$` (ASCII 36) precedes every letter.
-- ============================================================================
WITH RECURSIVE tree AS (
    SELECT sn.node_id, 0::int AS depth, sn.node_id AS root_id
    FROM skill_nodes sn JOIN career_roles cr ON cr.career_id = sn.career_id
    WHERE sn.parent_node IS NULL AND cr.career_name IN ('Frontend', 'Backend')
    UNION ALL
    SELECT c.node_id, t.depth + 1, t.root_id
    FROM skill_nodes c JOIN tree t ON c.parent_node = t.node_id
)
UPDATE skill_nodes sn SET depth = t.depth, root_node_id = t.root_id
FROM tree t
WHERE t.node_id = sn.node_id
  AND (sn.depth IS DISTINCT FROM t.depth OR sn.root_node_id IS DISTINCT FROM t.root_id);

WITH RECURSIVE desc_of AS (
    SELECT sn.node_id AS anc, c.node_id AS d
    FROM skill_nodes sn
    JOIN skill_nodes c ON c.parent_node = sn.node_id
    JOIN career_roles cr ON cr.career_id = sn.career_id
    WHERE cr.career_name IN ('Frontend', 'Backend')
    UNION
    SELECT dd.anc, c.node_id FROM desc_of dd JOIN skill_nodes c ON c.parent_node = dd.d
)
UPDATE skill_nodes sn SET subtree_size = x.n
FROM (SELECT sn2.node_id, (SELECT count(*) FROM desc_of WHERE anc = sn2.node_id) AS n
      FROM skill_nodes sn2 JOIN career_roles cr ON cr.career_id = sn2.career_id
      WHERE cr.career_name IN ('Frontend', 'Backend')) x
WHERE x.node_id = sn.node_id AND sn.subtree_size IS DISTINCT FROM x.n;

-- Expected afterwards:
--   Backend  spine  -> Internet, Pick a Language, Version Control, ... (15 roots)
--   Backend  chooser-> Python 122, Golang 123, Scala 155, C# 267, Rust 99,
--                      Node.js 82, Java 71, Kotlin 85, PHP 120
--   Frontend spine  -> Internet, HTML, CSS, JavaScript, ... (24 roots)
-- Every language option clears SubRoadmapClassifier.MIN_SUBTREE (12), so each
-- is a roadmap the student enters rather than a row they scroll past.

-- Undo:
--   UPDATE skill_nodes sn SET sort_order = u.old_sort_order
--   FROM skill_node_order_undo u WHERE u.node_id = sn.node_id;
--   UPDATE skill_nodes sn SET parent_node = u.old_parent, status = u.old_status
--   FROM skill_node_zone_undo u WHERE u.node_id = sn.node_id;
--   (then re-run the two recursive blocks above)
