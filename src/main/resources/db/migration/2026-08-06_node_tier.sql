-- ============================================================================
-- skill_nodes.tier — which student level a node is for
--
-- Run after 2026-08-06_node_kind_repair.sql, after every reseed.
--
-- WHY
-- ---
-- Every student opening Java's roadmap saw the same 71 nodes, including the
-- ones that only mean something after a year of writing Java. A roadmap that
-- does not change with the person reading it is a syllabus.
--
-- HOW tier IS DERIVED — and what the plan got wrong
-- -------------------------------------------------
-- The plan said `tier = 1 when depth <= 1 AND difficulty <= 2`. Measured before
-- writing this: `difficulty` is **NULL on all 4177 rows**. That formula would
-- have sent every node to the else-branch and filed 100% of the catalog as
-- Intermediate — the exact failure the plan's own verification step was written
-- to catch ("đừng để 90% rơi vào 1 bậc"). So difficulty is not usable, and this
-- derives from `depth`, which is populated everywhere.
--
-- Depth is a fair proxy here because of what depth *is* in this catalog: it is
-- how many refinements deep a node sits under its roadmap's root. `Java` is
-- depth 0, `Java → Basics` depth 1, `Java → Spring → Boot → Actuator` depth 3.
-- Specificity and prerequisite load rise together.
--
--   tier 1  Beginner      depth 0-1     1077 nodes
--   tier 2  Intermediate  depth 2       1633 nodes
--   tier 3  Advanced      depth 3+      1467 nodes
--
-- 26 / 39 / 35 — no bucket swallows the catalog.
--
-- NULL is meaningful: "show at every level". Nothing is set to NULL here, but a
-- mentor editing a node may want that, so the resolver treats NULL as visible
-- rather than as tier 1.
--
-- WHAT IT DOES NOT DO
-- -------------------
-- Nothing is hidden by this file. It stores a number; the decision to withhold
-- a node belongs to RoadmapTierResolver, where it can be read and changed
-- without a migration.
-- ============================================================================

ALTER TABLE skill_nodes ADD COLUMN IF NOT EXISTS tier SMALLINT;

ALTER TABLE skill_nodes DROP CONSTRAINT IF EXISTS ck_skill_nodes_tier;
ALTER TABLE skill_nodes ADD CONSTRAINT ck_skill_nodes_tier
    CHECK (tier IS NULL OR tier BETWEEN 1 AND 3);

-- DEPTH RELATIVE TO THE ROADMAP THE NODE IS IN, not to the career root.
--
-- Absolute depth was tried first and was visibly wrong the moment React's own
-- roadmap was opened: React sits at depth 2 (Frontend → Pick a Framework →
-- React), so `JSX` and `Props vs State` landed at depth 3+ and every single
-- node inside React was labelled Advanced. Those are the first two things
-- anyone learns about React.
--
-- The fix is to measure from the roadmap you are standing in. A node's depth is
-- taken relative to its nearest ancestor big enough to be entered as its own
-- roadmap (SubRoadmapClassifier.MIN_SUBTREE = 12) — so React's basics are
-- Beginner *within React*, exactly as Frontend's basics are Beginner within
-- Frontend. Nodes with no such ancestor keep their career-wide depth.
WITH RECURSIVE anc AS (
    -- Every (node, ancestor) pair, carrying the ancestor's depth and size.
    SELECT sn.node_id, sn.parent_node AS anc_id, sn.depth AS node_depth
    FROM skill_nodes sn WHERE sn.parent_node IS NOT NULL
    UNION ALL
    SELECT a.node_id, p.parent_node, a.node_depth
    FROM anc a JOIN skill_nodes p ON p.node_id = a.anc_id
    WHERE p.parent_node IS NOT NULL
), base AS (
    -- The SHALLOWEST enterable ancestor — the outermost roadmap the node sits
    -- inside. Taking the deepest was tried and collapsed the scale: plenty of
    -- mid-tree topics clear 12 descendants, so the baseline slid down to the
    -- node's own parent and 99.8% of the catalog came out Beginner or
    -- Intermediate. The outermost one is stable: inside React everything is
    -- measured from React, however many sizeable topics sit in between.
    --
    -- CHOOSE_ONE groups are excluded, matching SubRoadmapClassifier: `Pick a
    -- Framework` sums to 70 only because React is 42 and Vue is 32, and it
    -- teaches none of it. Counting it as the baseline would put React's own
    -- basics a level deeper than they are.
    SELECT a.node_id, a.node_depth, min(anc_node.depth) AS base_depth
    FROM anc a
    JOIN skill_nodes anc_node ON anc_node.node_id = a.anc_id
    WHERE coalesce(anc_node.subtree_size, 0) >= 12
      AND upper(coalesce(anc_node.selection, 'ALL')) <> 'CHOOSE_ONE'
    GROUP BY a.node_id, a.node_depth
), relative AS (
    SELECT sn.node_id,
           CASE WHEN sn.depth IS NULL THEN NULL
                ELSE sn.depth - coalesce(b.base_depth, 0) END AS rel_depth
    FROM skill_nodes sn LEFT JOIN base b ON b.node_id = sn.node_id
)
-- Recomputed in full every run rather than only where NULL: depth changes with
-- every reparenting migration, and a tier left over from the old depth is worse
-- than no tier at all — it withholds the wrong nodes and nothing says so.
UPDATE skill_nodes sn SET tier = CASE
        WHEN r.rel_depth IS NULL THEN NULL
        WHEN r.rel_depth <= 1 THEN 1
        WHEN r.rel_depth = 2 THEN 2
        ELSE 3
    END
FROM relative r
WHERE r.node_id = sn.node_id
  AND sn.tier IS DISTINCT FROM (CASE
        WHEN r.rel_depth IS NULL THEN NULL
        WHEN r.rel_depth <= 1 THEN 1
        WHEN r.rel_depth = 2 THEN 2
        ELSE 3
    END);

CREATE INDEX IF NOT EXISTS idx_skill_nodes_tier ON skill_nodes (tier);

-- Verify:
--   SELECT tier, count(*) FROM skill_nodes GROUP BY 1 ORDER BY 1;
--   Expect roughly 1077 / 1633 / 1467 and no bucket over 60%.
