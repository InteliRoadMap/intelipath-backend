-- ============================================================================
-- Evidence stuck at PENDING for good
--
-- One-off backfill. The behaviour is owned by SkillProficiencyPromoter from now
-- on; this only settles rows recorded before that existed.
--
-- WHY
-- ---
-- Acceptance used to happen in exactly one place: RoadmapPersonalizationServiceImpl,
-- and only for evidence attached to a roadmap node it had just completed. Two
-- consequences:
--
--   1. A skill the roadmap does not model could never be accepted. An analysed
--      repository named Spring Boot, raised the student to PROFESSIONAL and stamped
--      the row verified by GitHub — and the row stayed PENDING, because the Backend
--      tree has no node called `Spring Boot`, only `Spring (Spring Boot)` linked to
--      a different skill. Believed, acted upon, and displayed as unreviewed.
--
--   2. The self-assessment path never promoted at all, so none of its rows were
--      ever weighed by the promoter and none were ever closed.
--
-- PENDING now means what it says: recorded but not yet weighed.
--
-- WHAT IT DOES NOT DO
-- -------------------
-- REJECTED rows are left alone — those were deliberately superseded by a stronger
-- claim, and reopening them would resurrect a self-report a repository already
-- outranked.
--
-- A row whose skill_name matches nothing in the catalog is also left PENDING. That
-- is the one case where the word is still accurate: nothing has processed it,
-- because nothing could resolve it.
-- ============================================================================

CREATE TABLE IF NOT EXISTS student_evidence_status_undo (
    evidence_id UUID PRIMARY KEY,
    old_status  VARCHAR(30),
    new_status  VARCHAR(30),
    applied_at  TIMESTAMP DEFAULT NOW()
);

INSERT INTO student_evidence_status_undo (evidence_id, old_status, new_status)
SELECT e.evidence_id, e.status, 'ACCEPTED'
FROM student_skill_evidence e
WHERE e.status = 'PENDING'
  AND e.skill_name IS NOT NULL
  AND EXISTS (SELECT 1 FROM skills s WHERE lower(s.skill_name) = lower(e.skill_name))
ON CONFLICT (evidence_id) DO NOTHING;

UPDATE student_skill_evidence e
SET status = u.new_status
FROM student_evidence_status_undo u
WHERE u.evidence_id = e.evidence_id AND e.status IS DISTINCT FROM u.new_status;

-- Link each settled row to the student_skills row it stands for, so the portfolio
-- and level screens can trace a badge back to what earned it. Only fills blanks.
UPDATE student_skill_evidence e
SET student_skill_id = ss.student_skill_id
FROM student_evidence_status_undo u
JOIN student_skill_evidence e2 ON e2.evidence_id = u.evidence_id
JOIN skills s ON lower(s.skill_name) = lower(e2.skill_name)
JOIN student_skills ss ON ss.skill_id = s.skill_id AND ss.user_id = e2.user_id
WHERE e.evidence_id = u.evidence_id AND e.student_skill_id IS NULL;

-- Verify:
--   SELECT status, count(*) FROM student_skill_evidence GROUP BY 1;
--   -- any PENDING left should resolve to no catalog skill:
--   SELECT e.skill_name FROM student_skill_evidence e WHERE e.status = 'PENDING'
--     AND NOT EXISTS (SELECT 1 FROM skills s WHERE lower(s.skill_name) = lower(e.skill_name));
--
-- Undo:
--   UPDATE student_skill_evidence e SET status = u.old_status
--   FROM student_evidence_status_undo u WHERE u.evidence_id = e.evidence_id;
