-- Bridge long-lived installations into the versioned migration chain.
--
-- These objects originally lived in timestamp-named SQL files. Flyway ignores
-- those filenames, so an existing VPS baselined at 20260806 can reach V20260807.01
-- without them. This version sorts after the baseline and before V20260807.01.
-- Fresh installations already contain everything; every statement is idempotent.

CREATE TABLE IF NOT EXISTS recruitment_skills (
    link_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recruitment_id VARCHAR(255) NOT NULL,
    skill_id UUID NOT NULL,
    extracted_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rs_recruitment_skill UNIQUE (recruitment_id, skill_id),
    CONSTRAINT fk_rs_recruitment FOREIGN KEY (recruitment_id)
        REFERENCES recruitments(recruitment_id) ON DELETE CASCADE,
    CONSTRAINT fk_rs_skill FOREIGN KEY (skill_id)
        REFERENCES skills(skill_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_rs_skill ON recruitment_skills(skill_id);
CREATE INDEX IF NOT EXISTS idx_rs_recruitment ON recruitment_skills(recruitment_id);

ALTER TABLE recruitments ADD COLUMN IF NOT EXISTS career_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_recruit_career') THEN
        ALTER TABLE recruitments ADD CONSTRAINT fk_recruit_career
            FOREIGN KEY (career_id) REFERENCES career_roles(career_id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_recruit_career ON recruitments(career_id);

CREATE TABLE IF NOT EXISTS fpt_subject_skills (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_code VARCHAR(20) NOT NULL,
    skill_id UUID,
    skill_name VARCHAR(255) NOT NULL,
    CONSTRAINT fk_fss_subject FOREIGN KEY (subject_code)
        REFERENCES fpt_subjects(code) ON DELETE CASCADE,
    CONSTRAINT fk_fss_skill FOREIGN KEY (skill_id)
        REFERENCES skills(skill_id) ON DELETE SET NULL,
    CONSTRAINT uq_fss UNIQUE (subject_code, skill_name)
);

CREATE INDEX IF NOT EXISTS idx_fss_skill_name ON fpt_subject_skills(LOWER(skill_name));

ALTER TABLE skill_nodes ADD COLUMN IF NOT EXISTS tier SMALLINT;
ALTER TABLE skill_nodes ADD COLUMN IF NOT EXISTS is_checkpoint BOOLEAN DEFAULT FALSE;
