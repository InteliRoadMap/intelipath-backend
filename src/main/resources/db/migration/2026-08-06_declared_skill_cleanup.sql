-- ============================================================================
-- Remove the skill declarations that were made from a broken picker
--
-- WHY
-- ---
-- `SkillServiceImpl.getStudentSkills` served `skillRepository.findAll()` — all
-- 3.895 catalog rows. But `skills` is not a list of skills: it is also every node
-- title of every imported roadmap. So the picker offered `$elemMatch`, `--watch`,
-- `@else if`, `[global] keyword` and `Testing Methodologies & Techniques` for a
-- student to declare, sorted above `Android`, and students duly declared them.
--
-- 173 of 528 declaration rows name something no person can claim to be able to
-- do. They are not harmless. `SeniorityCalculator` divides held skills by the
-- career's core set, `AssessmentQuestionBuilder` puts a student's own
-- declarations FIRST in the question set, and the Skill Map draws one bubble per
-- declared skill. Measured before this ran, `vinh.student`'s Backend assessment
-- opened by asking about `Advanced Database Techniques`, `Data Definition
-- Language (DDL)` and `Android`.
--
-- The picker itself is fixed in code (`SkillRepository.findDeclarableCandidates`
-- plus `CoreSkillEligibility`), so no new junk can be declared. This file is only
-- the backfill for what was declared before that landed.
--
-- WHAT IS NOT TOUCHED
-- -------------------
-- 1. THE CATALOG. Not one row of `skills` is deleted. `$elemMatch` is still a
--    node inside the MongoDB track and still a thing to learn — it is simply not
--    a thing a student says they can do. Same line the whole cleanup has drawn.
--
-- 2. ANYTHING WITH EVIDENCE BEHIND IT. `student_skill_evidence` has ON DELETE
--    CASCADE from `student_skills`, so deleting a declaration silently deletes
--    the proof attached to it. Three rows here carry evidence (`Cloud`,
--    `Database`, `Authentication & Session Management`) and are left alone: a
--    badly-named row backed by a real GitHub repository is a naming problem, and
--    destroying the evidence to fix the name is the wrong trade.
--
-- 3. ANYTHING A SOURCE VERIFIED. One row (`Frontend`) carries
--    verified_by = 'GITHUB'. A category word is a poor skill name, but something
--    outside this system asserted it, and this migration is not the place to
--    overrule that.
--
-- That leaves 169 rows to delete, of 173 candidates.
--
-- RUN AS ONE TRANSACTION:
--   psql -1 -U intelipath -d intelipath -f 2026-08-06_declared_skill_cleanup.sql
-- ============================================================================

CREATE TABLE IF NOT EXISTS declared_skill_cleanup_undo (
    student_skill_id   uuid PRIMARY KEY,
    user_id            uuid        NOT NULL,
    skill_id           uuid        NOT NULL,
    skill_name         text,
    custom_description text,
    tech_stack         varchar(255),
    proficiency        smallint,
    self_declared      boolean,
    verified_by        varchar(30),
    updated_at         timestamp,
    removed_at         timestamp   NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- The candidates, by the same rules the running code now applies.
--
-- Mirrors CoreSkillEligibility: a leading code character, a name too long to be
-- a name, a list, a chapter title, or a category word. If that class is ever
-- changed, this file is stale rather than authoritative.
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE undeclarable ON COMMIT DROP AS
SELECT ss.student_skill_id
FROM student_skills ss
JOIN skills s ON s.skill_id = ss.skill_id
WHERE (
        -- a fragment of source code, not a name
        left(s.skill_name, 1) ~ '[\$@\[\]{}<>+*/\\#%=!?-]'
        -- past this the string is a sentence
     OR length(s.skill_name) > 40
        -- a list: " & ", " and ", " / " at word level
     OR s.skill_name ~* '\s(&|and|/|\+|,|or)\s'
        -- a chapter title, judged on the LAST word only: "Design Patterns" is a
        -- heading, "Pattern Matching" is a skill
     OR lower(regexp_replace(s.skill_name, '^.*\s', '')) IN (
            'fundamentals','basics','essentials','introduction','overview',
            'techniques','methodologies','methodology','concepts','topics',
            'tools','strategies','patterns','styles','principles')
        -- a category, compared whole: `Cloud` is a category, `Cloud Firestore`
        -- is a product
     OR lower(s.skill_name) IN (
            'cloud','cloud computing','api','apis','database','databases',
            'software','software development','software architecture','software engineering',
            'programming','coding','development','engineering',
            'automation','testing','test','architecture','design','integration',
            'monitoring','performance','security','frontend','front end','backend',
            'back end','full stack','fullstack','web','mobile','data','analytics',
            'devops','infrastructure','networking','operating systems','computer science')
  )
  -- The two protections. Note these are on the ROW, not on the name.
  AND ss.verified_by IS NULL
  AND NOT EXISTS (
        SELECT 1 FROM student_skill_evidence e
        WHERE e.student_skill_id = ss.student_skill_id);

INSERT INTO declared_skill_cleanup_undo (
    student_skill_id, user_id, skill_id, skill_name, custom_description,
    tech_stack, proficiency, self_declared, verified_by, updated_at)
SELECT ss.student_skill_id, ss.user_id, ss.skill_id, s.skill_name, ss.custom_description,
       ss.tech_stack, ss.proficiency, ss.self_declared, ss.verified_by, ss.updated_at
FROM student_skills ss
JOIN skills s ON s.skill_id = ss.skill_id
WHERE ss.student_skill_id IN (SELECT student_skill_id FROM undeclarable)
ON CONFLICT (student_skill_id) DO NOTHING;

DELETE FROM student_skills
WHERE student_skill_id IN (SELECT student_skill_id FROM undeclarable);

-- ---------------------------------------------------------------------------
-- Verify. `undeclarable_left` should be exactly the rows the two protections
-- kept — 4 on the database this was written against, never 0.
-- ---------------------------------------------------------------------------
SELECT (SELECT count(*) FROM declared_skill_cleanup_undo) AS removed,
       (SELECT count(*) FROM student_skills)              AS declarations_left,
       (SELECT count(*) FROM student_skill_evidence)      AS evidence_left;

SELECT u.username,
       count(*)                                              AS declares,
       count(*) FILTER (WHERE ss.verified_by IS NOT NULL)     AS verified
FROM student_skills ss
JOIN users u ON u.user_id = ss.user_id
GROUP BY 1 ORDER BY 2 DESC;

-- ============================================================================
-- UNDO
-- ============================================================================
--   INSERT INTO student_skills (student_skill_id, user_id, skill_id,
--       custom_description, tech_stack, proficiency, self_declared, verified_by,
--       updated_at)
--   SELECT student_skill_id, user_id, skill_id, custom_description, tech_stack,
--          proficiency, self_declared, verified_by, updated_at
--   FROM declared_skill_cleanup_undo
--   ON CONFLICT (student_skill_id) DO NOTHING;
--
--   DROP TABLE declared_skill_cleanup_undo;
--
-- The evidence rows are NOT restored by this, because none were deleted — the
-- filter above refused to touch any row that had some.
-- ============================================================================
