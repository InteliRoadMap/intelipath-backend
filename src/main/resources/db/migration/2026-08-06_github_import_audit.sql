-- ============================================================================
-- What the AI read, and what it answered
--
-- WHY
-- ---
-- Importing a repository spends a model call and then quietly rewrites the
-- student's profile: evidence rows appear, proficiency is promoted, roadmap
-- nodes complete. Until now the only record of how that happened was a log
-- line in the container — gone on the next rebuild, and never visible to the
-- student whose profile it changed.
--
-- The failure this closes is a real one. Three repositories were imported and
-- nothing completed; the summaries looked fine, so the import appeared to have
-- worked. It had not: a blank README meant the model was matching a repository
-- NAME against the whole catalog. Nobody could see that, because the size of
-- what was read was never recorded anywhere a person could look.
--
-- So this table stores the run's inputs and the model's raw answer. It stores
-- them ONCE, at analysis time, and never updates them — an audit that gets
-- rewritten is not an audit.
--
-- WHAT IT DELIBERATELY DOES NOT STORE
-- -----------------------------------
-- The accepted/rejected fate of each matched skill. That lives in
-- student_skill_evidence and keeps moving after the import (the promoter can
-- supersede a row hours later). Snapshotting it here would produce a screen
-- that confidently disagrees with the profile it is explaining, so the read
-- endpoint joins the live rows instead.
--
-- It also does not store the README text. The point is to show the student
-- what was read and how much of it, not to keep a second copy of their code.
-- ============================================================================

CREATE TABLE IF NOT EXISTS github_import_audit (
    audit_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL,
    repo_url       TEXT NOT NULL,
    repo_full_name TEXT,
    analyzed_at    TIMESTAMP NOT NULL DEFAULT NOW(),

    -- Which model answered, and how the files were fetched. Both change the
    -- result, and both are invisible from the outcome alone.
    model          VARCHAR(100),
    -- AUTHENTICATED = the student's own token via the Contents API (sees private
    -- repos); ANONYMOUS = raw.githubusercontent.com (public files only, and a
    -- private repo silently reads as empty here).
    fetch_mode     VARCHAR(20),

    -- Size of the catalog the model was asked to match against, and whose it was.
    -- 1466 skills and 200 skills are different questions; the first one measurably
    -- returned nothing.
    catalog_size   INT,
    career_name    TEXT,

    -- [{"path":"README.md","chars":12,"found":true}, ...] — one entry per file the
    -- importer attempted, including the ones that came back empty. An absent file
    -- is evidence too.
    sources        JSONB,

    -- The model's answer, exactly as returned, before the evidence layer touched it.
    summary        TEXT,
    tech_stack     JSONB,
    matched_skills JSONB,

    -- Re-importing the same repository replaces its audit: the student is looking
    -- at one repository, and two answers for it with no way to tell which produced
    -- the profile they see would be worse than one.
    CONSTRAINT uq_gia_user_repo UNIQUE (user_id, repo_url),
    CONSTRAINT ck_gia_fetch_mode
        CHECK (fetch_mode IN ('AUTHENTICATED', 'ANONYMOUS')),
    CONSTRAINT fk_gia_user
        FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_gia_user ON github_import_audit (user_id);

-- Verify:
--   SELECT repo_full_name, model, fetch_mode, catalog_size,
--          jsonb_array_length(sources) AS files_tried,
--          jsonb_array_length(matched_skills) AS matches
--   FROM github_import_audit ORDER BY analyzed_at DESC;
--
-- Undo:
--   DROP TABLE github_import_audit;
