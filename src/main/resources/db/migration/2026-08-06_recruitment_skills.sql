-- ============================================================================
-- Keep what the extraction already knew
--
-- WHY
-- ---
-- `skill_trends` answers "how many postings mentioned this skill" for the whole
-- market and nothing finer. That single number is why `career_required_skills`
-- had to be filled in by hand, and hand-filling produced a table where `Git` is
-- required by Data Science, DevOps, Frontend, Full Stack and QA but not Backend;
-- `CI/CD` is not required by DevOps; and `Testing` is not required by QA. The
-- omissions follow no rule, so they cannot be patched hole by hole.
--
-- The data to do better was already being computed and thrown away.
-- SkillExtractionServiceImpl calls `extractSkills(descriptions)`, which returns
-- one skill list PER POSTING, then immediately aggregates by date and drops the
-- per-posting detail. Nothing persisted it, so answering "which skills do
-- Backend postings ask for" meant paying the AI service to read all 913
-- descriptions again.
--
-- This table is that detail, kept. Re-running the extraction is now a one-time
-- cost rather than the price of every future question about the market.
--
-- WHY A CAREER COLUMN ON THE POSTING
-- ----------------------------------
-- The classification is derived from `recruitment_infos->>'title'` by keyword,
-- so it is cheap and repeatable — no model is involved and it can be recomputed
-- whenever the rules improve. It lives beside `seniority`, which is classified
-- the same way and already has `classified_at` next to it.
--
-- Nullable on purpose. A keyword pass over the current 913 titles resolves 494
-- of them; the remaining 419 stay NULL rather than being forced into the nearest
-- career. A posting counted for the wrong career is worse than one counted for
-- none, because it becomes evidence for a skill that role never asked for.
-- ============================================================================

CREATE TABLE IF NOT EXISTS recruitment_skills (
    link_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recruitment_id VARCHAR(255) NOT NULL,
    skill_id       UUID NOT NULL,
    extracted_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    -- One posting mentions a skill once, however many times the words appear.
    CONSTRAINT uq_rs_recruitment_skill UNIQUE (recruitment_id, skill_id),
    CONSTRAINT fk_rs_recruitment
        FOREIGN KEY (recruitment_id) REFERENCES recruitments (recruitment_id) ON DELETE CASCADE,
    CONSTRAINT fk_rs_skill
        FOREIGN KEY (skill_id) REFERENCES skills (skill_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_rs_skill ON recruitment_skills (skill_id);
CREATE INDEX IF NOT EXISTS idx_rs_recruitment ON recruitment_skills (recruitment_id);

ALTER TABLE recruitments
    ADD COLUMN IF NOT EXISTS career_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_recruit_career') THEN
        ALTER TABLE recruitments ADD CONSTRAINT fk_recruit_career
            FOREIGN KEY (career_id) REFERENCES career_roles (career_id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_recruit_career ON recruitments (career_id);

-- Verify (after the next extraction run):
--   SELECT c.career_name, count(DISTINCT rs.recruitment_id) AS postings,
--          count(DISTINCT rs.skill_id) AS distinct_skills
--   FROM recruitment_skills rs
--   JOIN recruitments r ON r.recruitment_id = rs.recruitment_id
--   JOIN career_roles c ON c.career_id = r.career_id
--   GROUP BY 1 ORDER BY 2 DESC;
--
--   -- what Backend postings actually ask for:
--   SELECT s.skill_name, count(DISTINCT rs.recruitment_id) AS postings
--   FROM recruitment_skills rs
--   JOIN recruitments r ON r.recruitment_id = rs.recruitment_id
--   JOIN skills s ON s.skill_id = rs.skill_id
--   WHERE r.career_id = (SELECT career_id FROM career_roles WHERE career_name='Backend')
--   GROUP BY 1 ORDER BY 2 DESC LIMIT 40;
--
-- Undo:
--   DROP TABLE recruitment_skills;
--   ALTER TABLE recruitments DROP COLUMN career_id;
