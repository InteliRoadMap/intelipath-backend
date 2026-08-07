-- ============================================================================
-- Choice options wearing their parent's skill
--
-- Run after 2026-08-06_node_tier.sql, after every reseed.
--
-- WHY
-- ---
-- 43 alternatives inside CHOOSE_ONE groups carry `skill_id` equal to their
-- GROUP's skill rather than their own. `Bun`, `npm`, `pnpm` and `yarn` all
-- point at the skill "Package Managers"; `Panda CSS` points at "Writing CSS".
--
-- Two consequences, and the second is the worse one:
--
--   1. The chooser reports "No posting data" for every option in the group,
--      because it looks up market demand by the option's skill and finds the
--      group's — which no posting names.
--
--   2. StackBranchScorer scores all four options IDENTICALLY. Its whole method
--      is Σ over distinct skills in the subtree, and four branches sharing one
--      skill produce one score four times. The tie test then fires and the
--      scorer correctly refuses to recommend anything — for a reason that has
--      nothing to do with the student.
--
-- StackBranchScorer's own javadoc already describes this fault in Backend
-- ("the node called JavaScript is linked to the skill 'Pick a Language'"). It
-- was never only Backend.
--
-- WHAT IT DOES NOT DO
-- -------------------
-- Only exact name matches are relinked: 41 of the 43. The remaining 2 are set
-- to NULL rather than guessed. An option with no skill draws no market bar and
-- contributes nothing to the score, which is honest; an option wearing the
-- wrong skill actively misreports both. Every change is recorded.
-- ============================================================================

CREATE TABLE IF NOT EXISTS skill_node_skill_undo (
    node_id     UUID PRIMARY KEY,
    old_skill   UUID,
    new_skill   UUID,
    applied_at  TIMESTAMP DEFAULT NOW()
);

WITH mislinked AS (
    SELECT c.node_id, c.node_name, c.skill_id AS old_skill
    FROM skill_nodes c
    JOIN skill_nodes p ON p.node_id = c.parent_node
    WHERE upper(coalesce(p.selection, 'ALL')) = 'CHOOSE_ONE'
      AND c.status IS DISTINCT FROM 'DRAFT'
      AND c.skill_id IS NOT NULL
      AND c.skill_id = p.skill_id
), resolved AS (
    -- Exact name only. A fuzzy match here would swap one wrong skill for
    -- another and hide the fact that it had ever been wrong.
    SELECT m.node_id, m.old_skill,
           (SELECT s.skill_id FROM skills s WHERE s.skill_name = m.node_name LIMIT 1) AS new_skill
    FROM mislinked m
)
INSERT INTO skill_node_skill_undo (node_id, old_skill, new_skill)
SELECT node_id, old_skill, new_skill FROM resolved
ON CONFLICT (node_id) DO NOTHING;

UPDATE skill_nodes sn SET skill_id = u.new_skill
FROM skill_node_skill_undo u
WHERE u.node_id = sn.node_id AND sn.skill_id IS DISTINCT FROM u.new_skill;

-- Verify: no option should share its group's skill afterwards.
--   SELECT count(*) FROM skill_nodes c JOIN skill_nodes p ON p.node_id = c.parent_node
--   WHERE upper(coalesce(p.selection,'ALL')) = 'CHOOSE_ONE'
--     AND c.skill_id IS NOT NULL AND c.skill_id = p.skill_id;
--   Expect 0.

-- Undo:
--   UPDATE skill_nodes sn SET skill_id = u.old_skill
--   FROM skill_node_skill_undo u WHERE u.node_id = sn.node_id;
