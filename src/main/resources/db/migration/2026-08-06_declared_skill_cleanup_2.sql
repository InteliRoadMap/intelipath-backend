-- ============================================================================
-- Second pass: the rows the first pass kept for a circular reason
--
-- WHY THERE IS A SECOND FILE
-- --------------------------
-- `2026-08-06_declared_skill_cleanup.sql` deleted 169 of 173 undeclarable
-- declarations and kept four. Measuring what protected those four showed that
-- three of them were protected by nothing:
--
--     skill                                verified_by   evidence source
--     ----------------------------------   -----------   --------------
--     Cloud                                (none)        MANUAL
--     Database                             (none)        MANUAL
--     Authentication & Session Management  (none)        MANUAL
--     Frontend                             GITHUB        (none)
--
-- MANUAL evidence is the self-report written back by the assessment. It exists
-- *because* the student declared the skill in the broken picker, so treating it
-- as grounds to keep the declaration is circular: the claim was cited as its own
-- corroboration. Only `Frontend` carries an assertion from outside this loop.
--
-- The first pass was written to protect anything with evidence behind it, which
-- was the right instinct and the wrong test. The test that means what it intended
-- is *external* evidence: verified_by set, or an evidence row whose source_type is
-- not MANUAL. That is what this file applies.
--
-- WHAT IS STILL NOT TOUCHED
-- -------------------------
-- 1. `Frontend`. verified_by = 'GITHUB'. A category word is a poor skill name, but
--    something outside this system asserted it, and a data-cleanup migration is
--    not the place to overrule an external verifier.
--
-- 2. QUESTION-NAMED ROWS BEYOND THE ONE. Twenty catalog rows are phrased as
--    questions — "What is a Domain Name?", "How RDB Works?" — and one had been
--    declared. `CoreSkillEligibility` now rejects a trailing '?', so the picker
--    will not offer them again; this file removes the one that got through.
--
-- 3. SKILLS THE MARKET IS SILENT ABOUT. `Tailwind`, `Common Table Expressions`,
--    `DDL` and `DML` are no longer *offered* by the picker, because it lists only
--    skills a posting or a career grade vouches for. That gate decides what to
--    show in a list of 700; it is not a test of whether an existing declaration is
--    true. Tailwind is a real skill somebody really knows, and deleting their
--    declaration because the crawler has not seen the word would be the same
--    mistake `CareerSkillDemandDeriver` already warns against: absence of postings
--    is absence of evidence, not evidence of absence.
--
-- RUN AS ONE TRANSACTION, AFTER THE FIRST PASS:
--   psql -1 -U intelipath -d intelipath -f 2026-08-06_declared_skill_cleanup_2.sql
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

CREATE TEMP TABLE undeclarable_2 ON COMMIT DROP AS
SELECT ss.student_skill_id
FROM student_skills ss
JOIN skills s ON s.skill_id = ss.skill_id
WHERE (
        left(s.skill_name, 1) ~ '[\$@\[\]{}<>+*/\\#%=!?-]'
        -- New in this pass: a question is a lesson title, not a skill.
     OR s.skill_name LIKE '%?'
     OR length(s.skill_name) > 40
     OR s.skill_name ~* '\s(&|and|/|\+|,|or)\s'
     OR lower(regexp_replace(s.skill_name, '^.*\s', '')) IN (
            'fundamentals','basics','essentials','introduction','overview',
            'techniques','methodologies','methodology','concepts','topics',
            'tools','strategies','patterns','styles','principles')
     OR lower(s.skill_name) IN (
            'cloud','cloud computing','api','apis','database','databases',
            'software','software development','software architecture','software engineering',
            'programming','coding','development','engineering',
            'automation','testing','test','architecture','design','integration',
            'monitoring','performance','security','frontend','front end','backend',
            'back end','full stack','fullstack','web','mobile','data','analytics',
            'devops','infrastructure','networking','operating systems','computer science')
  )
  -- The corrected protection: EXTERNAL evidence only.
  AND ss.verified_by IS NULL
  AND NOT EXISTS (
        SELECT 1 FROM student_skill_evidence e
        WHERE e.student_skill_id = ss.student_skill_id
          AND upper(coalesce(e.source_type, 'MANUAL')) <> 'MANUAL');

INSERT INTO declared_skill_cleanup_undo (
    student_skill_id, user_id, skill_id, skill_name, custom_description,
    tech_stack, proficiency, self_declared, verified_by, updated_at)
SELECT ss.student_skill_id, ss.user_id, ss.skill_id, s.skill_name, ss.custom_description,
       ss.tech_stack, ss.proficiency, ss.self_declared, ss.verified_by, ss.updated_at
FROM student_skills ss
JOIN skills s ON s.skill_id = ss.skill_id
WHERE ss.student_skill_id IN (SELECT student_skill_id FROM undeclarable_2)
ON CONFLICT (student_skill_id) DO NOTHING;

-- The MANUAL evidence rows attached to these go with them, by ON DELETE CASCADE.
-- That is correct here and not a loss: those rows say "the student said so", and
-- the thing they said is what is being removed.
DELETE FROM student_skills
WHERE student_skill_id IN (SELECT student_skill_id FROM undeclarable_2);

-- ---------------------------------------------------------------------------
-- Verify. `undeclarable_left` should now be exactly 1 — `Frontend`, held by its
-- GitHub verification — and the GITHUB_PROJECT evidence count must not have moved.
-- ---------------------------------------------------------------------------
SELECT (SELECT count(*) FROM declared_skill_cleanup_undo)                       AS removed_total,
       (SELECT count(*) FROM student_skills)                                    AS declarations_left,
       (SELECT count(*) FROM student_skill_evidence
         WHERE upper(coalesce(source_type,'MANUAL')) <> 'MANUAL')               AS external_evidence_left;

SELECT s.skill_name, ss.verified_by
FROM student_skills ss JOIN skills s ON s.skill_id = ss.skill_id
WHERE s.skill_name LIKE '%?'
   OR s.skill_name ~* '\s(&|and|/)\s'
   OR lower(s.skill_name) IN ('cloud','database','api','testing','security','frontend','backend')
ORDER BY 1;

-- ============================================================================
-- UNDO
-- ============================================================================
-- The undo table is shared with the first pass, so this restores BOTH passes.
--
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
-- The MANUAL evidence deleted by cascade in this pass is NOT restored. It was a
-- restatement of the declaration, and the next assessment writes it again.
-- ============================================================================
