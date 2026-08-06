-- ============================================================================
-- Who wrote it, not just what it is
--
-- WHY
-- ---
-- The first audit answered "what did the AI read?" and the honest answer was: a
-- README and one config file. Nothing in the pipeline had ever asked whether the
-- student wrote a line of the repository they were being credited for. The
-- closest thing to a check was isWorkedInFork(), which passes on a one-line
-- README edit — it tells you a fork was touched, never by whom.
--
-- The prompt made the gap explicit without closing it: it asked the model to
-- score "how strongly the CODE (not just a mention) shows the skill" while
-- handing it no code at all.
--
-- These columns record the answer to the question that was missing.
--
-- THREE VERDICTS, NOT TWO
-- -----------------------
-- CONTRIBUTED / NOT_CONTRIBUTED / UNKNOWN. The third is the important one.
-- Only NOT_CONTRIBUTED blocks evidence, and it is written only when GitHub
-- answered with a real contributor list that does not credit this student.
-- A rate limit, an outage, or statistics GitHub declined to compute all record
-- UNKNOWN, because collapsing them into NOT_CONTRIBUTED would let a failed
-- request accuse a student of padding their portfolio.
-- ============================================================================

ALTER TABLE github_import_audit
    -- CONTRIBUTED | NOT_CONTRIBUTED | UNKNOWN
    ADD COLUMN IF NOT EXISTS authorship_verdict VARCHAR(20),
    -- The login checked. Null when no GitHub account was linked at analysis time.
    ADD COLUMN IF NOT EXISTS author_login       TEXT,
    ADD COLUMN IF NOT EXISTS author_commits     INT,
    ADD COLUMN IF NOT EXISTS total_commits      INT,
    -- Plain-language explanation, shown to the student as-is. A verdict without a
    -- reason is an accusation without a charge.
    ADD COLUMN IF NOT EXISTS authorship_reason  TEXT,
    -- GitHub's own byte count per language: the closest thing to reading the code
    -- that costs one request, and the check on a config file that declares a
    -- framework the repository barely uses.
    ADD COLUMN IF NOT EXISTS language_bytes     JSONB,
    -- The student's own commit subjects. What a repository IS and what one person
    -- DID in it are different questions, and only the first was ever being asked.
    ADD COLUMN IF NOT EXISTS commit_subjects    JSONB,
    -- True when the verdict withheld skill evidence, so the audit screen can say
    -- why a project changed nothing.
    ADD COLUMN IF NOT EXISTS evidence_blocked   BOOLEAN DEFAULT FALSE;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_gia_authorship_verdict') THEN
        ALTER TABLE github_import_audit ADD CONSTRAINT ck_gia_authorship_verdict
            CHECK (authorship_verdict IS NULL
                   OR authorship_verdict IN ('CONTRIBUTED', 'NOT_CONTRIBUTED', 'UNKNOWN'));
    END IF;
END $$;

-- Verify:
--   SELECT repo_full_name, authorship_verdict, author_commits, total_commits,
--          evidence_blocked, jsonb_array_length(commit_subjects) AS commits_read
--   FROM github_import_audit ORDER BY analyzed_at DESC;
--
-- Undo:
--   ALTER TABLE github_import_audit
--     DROP COLUMN authorship_verdict, DROP COLUMN author_login,
--     DROP COLUMN author_commits, DROP COLUMN total_commits,
--     DROP COLUMN authorship_reason, DROP COLUMN language_bytes,
--     DROP COLUMN commit_subjects, DROP COLUMN evidence_blocked;
