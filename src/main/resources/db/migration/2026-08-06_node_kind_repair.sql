-- ============================================================================
-- node_kind derived from the parent, not guessed from the name
--
-- Run after 2026-08-05_roadmap_spine_order.sql, after every reseed.
--
-- WHY
-- ---
-- `skill_nodes.node_kind` (CORE / ALTERNATIVE / OPTIONAL) is what tells a
-- client "this node is one of several ways to satisfy its parent, not a step
-- you walk". The seeder fills it from the CSV, and the CSV disagreed with
-- itself: of the nine children of Backend's `Pick a Language`, Python, C#,
-- Rust, Java and PHP were ALTERNATIVE while Scala, Golang, Kotlin and Node.js
-- were CORE -- same group, same role, opposite label.
--
-- Measured before writing this: 20 children of a CHOOSE_ONE group carried
-- CORE, and 1 child of an ALL group carried ALTERNATIVE. 21 rows.
--
-- Whether a node is an alternative is not a property of the node. It is a
-- property of the group above it: a child of CHOOSE_ONE is an alternative, a
-- child of ALL is not. So derive it from the parent and stop storing an
-- independent opinion that can drift.
--
-- WHAT IT DOES NOT DO
-- -------------------
-- Rows already marked OPTIONAL are left alone -- that is a real third state
-- (a node you may skip inside an ALL group), and the parent cannot express it.
-- There are currently zero such rows; the guard is there so a later editorial
-- decision is not silently reverted by the next reseed.
--
-- Roots (parent_node IS NULL) are left alone: nothing above them to derive
-- from, and all 141 are already CORE.
-- ============================================================================

CREATE TABLE IF NOT EXISTS skill_node_kind_undo (
    node_id       UUID PRIMARY KEY,
    old_node_kind TEXT,
    new_node_kind TEXT,
    applied_at    TIMESTAMP DEFAULT NOW()
);

WITH derived AS (
    SELECT c.node_id,
           c.node_kind AS old_kind,
           CASE WHEN upper(coalesce(p.selection, 'ALL')) = 'CHOOSE_ONE'
                THEN 'ALTERNATIVE' ELSE 'CORE' END AS new_kind
    FROM skill_nodes c
    JOIN skill_nodes p ON p.node_id = c.parent_node
    WHERE c.node_kind IS DISTINCT FROM 'OPTIONAL'
)
INSERT INTO skill_node_kind_undo (node_id, old_node_kind, new_node_kind)
SELECT node_id, old_kind, new_kind
FROM derived
WHERE old_kind IS DISTINCT FROM new_kind
ON CONFLICT (node_id) DO NOTHING;

UPDATE skill_nodes sn SET node_kind = u.new_node_kind
FROM skill_node_kind_undo u
WHERE u.node_id = sn.node_id AND sn.node_kind IS DISTINCT FROM u.new_node_kind;

-- Expected afterwards: every (parent.selection, child.node_kind) pair is either
--   (ALL, CORE) or (CHOOSE_ONE, ALTERNATIVE) -- plus any OPTIONAL rows and the
--   roots. Verify with:
--
--   SELECT coalesce(p.selection,'(root)'), c.node_kind, count(*)
--   FROM skill_nodes c LEFT JOIN skill_nodes p ON p.node_id = c.parent_node
--   GROUP BY 1,2 ORDER BY 1,2;

-- Undo:
--   UPDATE skill_nodes sn SET node_kind = u.old_node_kind
--   FROM skill_node_kind_undo u WHERE u.node_id = sn.node_id;
