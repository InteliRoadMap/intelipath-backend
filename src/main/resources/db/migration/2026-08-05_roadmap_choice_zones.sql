-- ============================================================================
-- Roadmap choice zones — Frontend / Backend / Full Stack
--
-- Run after every reseed, alongside standardize-roadmap-structure.sql. The
-- seeder rebuilds skill_nodes from CSV and does not know about any of this.
--
-- WHY
-- ---
-- A CHOOSE_ONE group is the one place on the roadmap where the student makes a
-- decision that reshapes everything after it. Three faults made those groups
-- unusable, and all three were invisible until the groups were listed out:
--
--   1. The real track sat OUTSIDE the chooser. `Node.js` carries 82 descendants
--      and hung at depth 0 as its own root, while Backend's `Pick a Language`
--      offered two empty stubs -- `JavaScript` (0) and `JavaScript (Node.js)`
--      (0) -- for the same language. Frontend had it too: `Vue` (32) at the
--      root, `Vue.js` (0) inside `Pick a Framework`. So the largest language
--      track in Backend was not in the language chooser at all.
--
--   2. The same option appeared more than once. Frontend's `GraphQL` group
--      listed `Apollo` twice plus `Apollo Client`, and both `Relay` and
--      `Relay Modern`.
--
--   3. A choice of one. `Static Site Generation` was CHOOSE_ONE with exactly
--      one option under it.
--
-- WHAT IT DOES NOT DO
-- -------------------
-- Nothing is deleted. Duplicates are set to DRAFT, which the FR2.3 publication
-- gate already understands: withheld from students, still there for the editor
-- to merge or revive. Every change is recorded in skill_node_zone_undo.
-- ============================================================================

CREATE TABLE IF NOT EXISTS skill_node_zone_undo (
    node_id       UUID PRIMARY KEY,
    old_parent    UUID,
    new_parent    UUID,
    old_status    TEXT,
    new_status    TEXT,
    old_selection TEXT,
    new_selection TEXT,
    applied_at    TIMESTAMP DEFAULT NOW()
);

-- Resolved by name within career rather than by hard-coded UUID: the seeder
-- mints new ids on every reseed, so ids from a previous run are worthless here.
WITH target AS (
    SELECT sn.node_id, cr.career_name, sn.node_name, sn.parent_node, sn.status, sn.selection,
           coalesce(sn.subtree_size, 0) AS sub
    FROM skill_nodes sn JOIN career_roles cr ON cr.career_id = sn.career_id
    WHERE cr.career_name IN ('Frontend', 'Backend', 'Full Stack')
), plan AS (
    -- Move the real track into the chooser it belongs to.
    SELECT t.node_id,
           (SELECT node_id FROM target WHERE career_name = 'Frontend'
              AND node_name = 'Pick a Framework' LIMIT 1) AS new_parent,
           NULL::text AS new_status, NULL::text AS new_selection
    FROM target t WHERE t.career_name = 'Frontend' AND t.node_name = 'Vue' AND t.sub > 10
    UNION ALL
    SELECT t.node_id,
           (SELECT node_id FROM target WHERE career_name = 'Backend'
              AND node_name = 'Pick a Language' LIMIT 1),
           NULL, NULL
    FROM target t WHERE t.career_name = 'Backend' AND t.node_name = 'Node.js' AND t.sub > 10
    UNION ALL
    -- Withhold the empty duplicates the real track replaces. Guarded on sub = 0
    -- so this can never withhold a node that has since been filled in.
    SELECT t.node_id, NULL, 'DRAFT', NULL
    FROM target t
    WHERE t.sub = 0 AND (
            (t.career_name = 'Frontend' AND t.node_name IN ('Vue.js', 'Relay Modern'))
         OR (t.career_name = 'Backend'  AND t.node_name IN ('JavaScript', 'JavaScript (Node.js)')
             AND t.parent_node = (SELECT node_id FROM target
                                  WHERE career_name = 'Backend' AND node_name = 'Pick a Language' LIMIT 1)))
    UNION ALL
    -- Apollo appears twice under GraphQL; Apollo Client is the one that keeps
    -- its name honest, so the bare duplicates go.
    SELECT t.node_id, NULL, 'DRAFT', NULL
    FROM target t
    WHERE t.career_name = 'Frontend' AND t.node_name = 'Apollo' AND t.sub = 0
      AND t.parent_node = (SELECT node_id FROM target
                           WHERE career_name = 'Frontend' AND node_name = 'GraphQL' LIMIT 1)
    UNION ALL
    -- A group with one option underneath is not a decision.
    SELECT t.node_id, NULL, NULL, 'ALL'
    FROM target t
    WHERE t.selection = 'CHOOSE_ONE'
      AND (SELECT count(*) FROM skill_nodes c
           WHERE c.parent_node = t.node_id AND c.status <> 'DRAFT') <= 1
)
INSERT INTO skill_node_zone_undo
    (node_id, old_parent, new_parent, old_status, new_status, old_selection, new_selection)
SELECT p.node_id, sn.parent_node, p.new_parent, sn.status, p.new_status, sn.selection, p.new_selection
FROM plan p JOIN skill_nodes sn ON sn.node_id = p.node_id
ON CONFLICT (node_id) DO NOTHING;

UPDATE skill_nodes sn SET
    parent_node = coalesce(u.new_parent, sn.parent_node),
    status      = coalesce(u.new_status, sn.status),
    selection   = coalesce(u.new_selection, sn.selection)
FROM skill_node_zone_undo u
WHERE u.node_id = sn.node_id;

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
    WHERE sn.parent_node IS NULL AND cr.career_name IN ('Frontend', 'Backend', 'Full Stack')
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
    WHERE cr.career_name IN ('Frontend', 'Backend', 'Full Stack')
    UNION
    SELECT dd.anc, c.node_id FROM desc_of dd JOIN skill_nodes c ON c.parent_node = dd.d
)
UPDATE skill_nodes sn SET subtree_size = x.n
FROM (SELECT sn2.node_id, (SELECT count(*) FROM desc_of WHERE anc = sn2.node_id) AS n
      FROM skill_nodes sn2 JOIN career_roles cr ON cr.career_id = sn2.career_id
      WHERE cr.career_name IN ('Frontend', 'Backend', 'Full Stack')) x
WHERE x.node_id = sn.node_id AND sn.subtree_size IS DISTINCT FROM x.n;

-- Expected afterwards:
--   Backend  Pick a Language  -> Python 122, C# 110, Rust 99, Node.js 82, Java 71, PHP 41
--   Frontend Pick a Framework -> React 42, Vue 32, Svelte 0
-- Every Backend option clears SubRoadmapClassifier.MIN_SUBTREE (12), so each
-- language becomes a roadmap the student enters rather than a row they scroll past.

-- Undo:
--   UPDATE skill_nodes sn SET parent_node = u.old_parent, status = u.old_status,
--                             selection = u.old_selection
--   FROM skill_node_zone_undo u WHERE u.node_id = sn.node_id;
--   (then re-run the two recursive blocks above)
