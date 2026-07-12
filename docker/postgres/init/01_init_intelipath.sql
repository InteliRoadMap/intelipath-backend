-- ============================================================
-- InteliPath local Docker database bootstrap
-- Runs once when the postgres_data volume is first created.
--
--   docker compose down -v
--   docker compose up --build
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================================
-- Core lookup/profile tables
-- ============================================================

CREATE TABLE IF NOT EXISTS career_roles (
    career_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    career_name     VARCHAR(255) NOT NULL UNIQUE,
    prerequisite    JSONB,
    description     TEXT
);

CREATE TABLE IF NOT EXISTS skills (
    skill_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category        VARCHAR(255),
    careers         JSONB,
    skill_name      VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS universities (
    university_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(50) UNIQUE,
    name            VARCHAR(255) NOT NULL,
    logo_url        TEXT,
    domain_email    VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS users (
    user_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) UNIQUE NOT NULL,
    full_name       VARCHAR(255),
    yob             DATE,
    bio             TEXT,
    avatar_url      TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    role            VARCHAR(30) NOT NULL DEFAULT 'STUDENT',
    account_status  VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT ck_users_role
        CHECK (role IN ('STUDENT', 'COUNSELOR', 'MENTOR', 'ADMIN')),
    CONSTRAINT ck_users_account_status
        CHECK (account_status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED'))
);

CREATE TABLE IF NOT EXISTS students (
    user_id             UUID PRIMARY KEY,
    career_id           UUID,
    university_id        UUID,
    university_name      VARCHAR(255),
    year_of_admission   INT,
    major               VARCHAR(255),
    github_profile      VARCHAR(255),
    transcript_url      TEXT,
    portfolio_slug      VARCHAR(100) UNIQUE,
    CONSTRAINT fk_st_user
        FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_st_career
        FOREIGN KEY (career_id) REFERENCES career_roles (career_id) ON DELETE SET NULL,
    CONSTRAINT fk_st_university
        FOREIGN KEY (university_id) REFERENCES universities (university_id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS academic_counselor (
    user_id             UUID PRIMARY KEY,
    university_id        UUID,
    department          VARCHAR(255),
    year_of_admission   INT,
    CONSTRAINT fk_ac_user
        FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_ac_university
        FOREIGN KEY (university_id) REFERENCES universities (university_id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS industry_mentor (
    user_id        UUID PRIMARY KEY,
    company        VARCHAR(255),
    industry_focus VARCHAR(255),
    CONSTRAINT fk_im_user
        FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
);

-- ============================================================
-- Skill / roadmap
-- ============================================================

CREATE TABLE IF NOT EXISTS career_required_skills (
    skill_required_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    career_id         UUID NOT NULL,
    skill_id          UUID NOT NULL,
    importance_level  VARCHAR(20),
    CONSTRAINT uq_career_skill UNIQUE (career_id, skill_id),
    CONSTRAINT ck_crs_importance
        CHECK (importance_level IS NULL OR importance_level IN ('LOW', 'AVG', 'HIGH')),
    CONSTRAINT fk_crs_career
        FOREIGN KEY (career_id) REFERENCES career_roles (career_id) ON DELETE CASCADE,
    CONSTRAINT fk_crs_skill
        FOREIGN KEY (skill_id) REFERENCES skills (skill_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS node_types (
    type_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stage               VARCHAR(30) NOT NULL DEFAULT 'FOUNDATION',
    unlock_key_required BOOLEAN,
    stage_unlock_key    JSONB,
    weight              INT,
    CONSTRAINT ck_node_types_stage
        CHECK (stage IN ('FOUNDATION', 'CORE', 'PRACTICAL', 'ADVANCED', 'JOB_READY'))
);

CREATE TABLE IF NOT EXISTS skill_nodes (
    node_id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    career_id            UUID NOT NULL,
    skill_id             UUID,
    type_id              UUID,
    previous_node        UUID,
    parent_node          UUID,
    prerequisite         JSONB,
    node_name            VARCHAR(255) NOT NULL,
    node_level           INT,
    description          TEXT,
    resource             JSONB,
    completion_policy    VARCHAR(50) DEFAULT 'NEVER_COMPLETE',
    required_proficiency INT,
    evidence_keywords    JSONB,
    CONSTRAINT ck_skill_nodes_completion_policy
        CHECK (completion_policy IS NULL OR completion_policy IN ('NEVER_COMPLETE', 'MANUAL_ONLY', 'EVIDENCE_ALLOWED')),
    CONSTRAINT fk_sn_career
        FOREIGN KEY (career_id) REFERENCES career_roles (career_id) ON DELETE CASCADE,
    CONSTRAINT fk_sn_skill
        FOREIGN KEY (skill_id) REFERENCES skills (skill_id) ON DELETE SET NULL,
    CONSTRAINT fk_sn_type
        FOREIGN KEY (type_id) REFERENCES node_types (type_id) ON DELETE SET NULL,
    CONSTRAINT fk_sn_previous_node
        FOREIGN KEY (previous_node) REFERENCES skill_nodes (node_id) ON DELETE SET NULL,
    CONSTRAINT fk_sn_parent_node
        FOREIGN KEY (parent_node) REFERENCES skill_nodes (node_id) ON DELETE SET NULL
);

-- ============================================================
-- Roadmap layout (presentation only)
-- ============================================================
-- Purely visual placement of a node on the roadmap canvas, edited by mentors.
-- Kept separate from skill_nodes so layout never influences unlock/progress
-- logic; the dynamic roadmap is still computed from parent/previous/
-- prerequisite/stage/student_progress. One row per node.

CREATE TABLE IF NOT EXISTS roadmap_node_layouts (
    layout_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    node_id        UUID NOT NULL,
    position_x     DOUBLE PRECISION,
    position_y     DOUBLE PRECISION,
    lane           VARCHAR(50),
    display_order  INT,
    layout_version INT NOT NULL DEFAULT 1,
    edited_by      UUID,
    updated_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_roadmap_node_layout UNIQUE (node_id),
    CONSTRAINT fk_rnl_node
        FOREIGN KEY (node_id) REFERENCES skill_nodes (node_id) ON DELETE CASCADE,
    CONSTRAINT fk_rnl_edited_by
        FOREIGN KEY (edited_by) REFERENCES users (user_id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS student_skills (
    student_skill_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID NOT NULL,
    skill_id           UUID NOT NULL,
    custom_description TEXT,
    tech_stack         VARCHAR(255),
    CONSTRAINT uq_student_skill UNIQUE (user_id, skill_id),
    CONSTRAINT fk_ss_student
        FOREIGN KEY (user_id) REFERENCES students (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_ss_skill
        FOREIGN KEY (skill_id) REFERENCES skills (skill_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS student_progress (
    progress_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL,
    node_id      UUID NOT NULL,
    status       VARCHAR(30) NOT NULL DEFAULT 'IN_PROGRESS',
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP,
    CONSTRAINT uq_student_progress UNIQUE (user_id, node_id),
    CONSTRAINT ck_student_progress_status
        CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'LOCKED')),
    CONSTRAINT fk_sp_student
        FOREIGN KEY (user_id) REFERENCES students (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_sp_node
        FOREIGN KEY (node_id) REFERENCES skill_nodes (node_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS skill_trends (
    trend_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    skill_id    UUID NOT NULL,
    jobs_needed INT,
    week_stamp  DATE,
    CONSTRAINT fk_strd_skill
        FOREIGN KEY (skill_id) REFERENCES skills (skill_id) ON DELETE CASCADE
);

-- ============================================================
-- Portfolio / mentor feedback
-- ============================================================

CREATE TABLE IF NOT EXISTS portfolio_configs (
    config_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID UNIQUE NOT NULL,
    theme          VARCHAR(50) DEFAULT 'dark',
    theme_colors   JSONB,
    fonts          JSONB,
    hero_section   JSONB,
    skills_section JSONB,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_pc_user
        FOREIGN KEY (user_id) REFERENCES students (user_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS student_education (
    education_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL,
    university   VARCHAR(255) NOT NULL,
    degree       VARCHAR(255),
    period       VARCHAR(100),
    description  TEXT,
    CONSTRAINT fk_se_user
        FOREIGN KEY (user_id) REFERENCES students (user_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS portfolio_project (
    project_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL,
    repo_id      BIGINT,
    repo_url     TEXT,
    project_name VARCHAR(255) NOT NULL DEFAULT 'Untitled Project',
    demo_url     TEXT,
    icon         VARCHAR(100),
    description  TEXT,
    stars        INT DEFAULT 0,
    tech_stack   JSONB,
    CONSTRAINT fk_pp_user
        FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS feedback (
    feedback_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id   UUID NOT NULL,
    receiver_id UUID NOT NULL,
    sender_name VARCHAR(255),
    content     TEXT,
    type        VARCHAR(30) DEFAULT 'GENERAL',
    status      VARCHAR(30) DEFAULT 'NEW',
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_feedback_type
        CHECK (type IS NULL OR type IN ('GENERAL', 'SKILL', 'CAREER', 'PORTFOLIO')),
    CONSTRAINT ck_feedback_status
        CHECK (status IS NULL OR status IN ('NEW', 'READ', 'UPDATED', 'DELETED')),
    CONSTRAINT fk_fb_sender
        FOREIGN KEY (sender_id) REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_fb_receiver
        FOREIGN KEY (receiver_id) REFERENCES users (user_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS feedback_attachment (
    attachment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    feedback_id   UUID         NOT NULL REFERENCES feedback(feedback_id) ON DELETE CASCADE,
    file_name     VARCHAR(255) NOT NULL,
    file_type     VARCHAR(100),
    file_size     BIGINT,
    data          BYTEA        NOT NULL
);

CREATE TABLE IF NOT EXISTS portfolio_review_requests (
    request_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id  UUID NOT NULL,
    mentor_id   UUID NOT NULL,
    status      VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    create_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMP,
    CONSTRAINT ck_prr_status
        CHECK (status IN ('PENDING', 'REVIEWED', 'REJECTED')),
    CONSTRAINT fk_prr_student
        FOREIGN KEY (student_id) REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_prr_mentor
        FOREIGN KEY (mentor_id) REFERENCES users (user_id) ON DELETE CASCADE
);

-- ============================================================
-- AI evidence / roadmap recommendations
-- ============================================================

CREATE TABLE IF NOT EXISTS student_skill_evidence (
    evidence_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Owner of the evidence. Evidence arrives before the student accepts the
    -- skill, so student_skill_id stays NULL until an accepted recommendation
    -- creates the student_skills row and back-fills the link.
    user_id          UUID NOT NULL,
    student_skill_id UUID,
    node_id          UUID,
    skill_name       VARCHAR(255),
    source_type      VARCHAR(30) NOT NULL,
    source_id        UUID,
    source_url       TEXT,
    evidence_text    TEXT,
    confidence       NUMERIC(5,2),
    detected_by      VARCHAR(50) DEFAULT 'ai-service',
    detected_at      TIMESTAMP,
    status           VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    CONSTRAINT ck_sse_source_type
        CHECK (source_type IN ('GITHUB_PROJECT', 'TRANSCRIPT', 'CHAT_FILE', 'MANUAL')),
    CONSTRAINT ck_sse_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED')),
    CONSTRAINT fk_sse_user
        FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_sse_student_skill
        FOREIGN KEY (student_skill_id) REFERENCES student_skills (student_skill_id) ON DELETE CASCADE,
    CONSTRAINT fk_sse_node
        FOREIGN KEY (node_id) REFERENCES skill_nodes (node_id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS roadmap_recommendations (
    recommendation_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID,
    current_career_id   UUID NOT NULL,
    recommend_career_id UUID NOT NULL,
    recommendation_type VARCHAR(50) NOT NULL DEFAULT 'SKIP_KNOWN_SKILLS',
    title               TEXT,
    summary             TEXT,
    reason              TEXT,
    confidence          NUMERIC(5,2),
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    decided_at          TIMESTAMP,
    CONSTRAINT ck_rr_type
        CHECK (recommendation_type IN ('SKIP_KNOWN_SKILLS', 'FAST_TRACK', 'CHANGE_PATH', 'ADD_ADVANCED_TOPICS')),
    CONSTRAINT ck_rr_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED')),
    CONSTRAINT fk_rr_user
        FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_rr_current_career
        FOREIGN KEY (current_career_id) REFERENCES career_roles (career_id) ON DELETE CASCADE,
    CONSTRAINT fk_rr_recommend_career
        FOREIGN KEY (recommend_career_id) REFERENCES career_roles (career_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS roadmap_recommendation_items (
    rec_item_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recommendation_id UUID NOT NULL,
    node_id           UUID NOT NULL,
    action            VARCHAR(30) NOT NULL DEFAULT 'MARK_COMPLETE',
    reason            TEXT,
    evidence_ids      UUID[],
    confidence        NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    CONSTRAINT ck_rri_action
        CHECK (action IN ('MARK_COMPLETE', 'SKIP', 'UNLOCK', 'PRIORITIZE', 'ADD', 'REMOVE')),
    CONSTRAINT fk_rri_recommendation
        FOREIGN KEY (recommendation_id) REFERENCES roadmap_recommendations (recommendation_id) ON DELETE CASCADE,
    CONSTRAINT fk_rri_node
        FOREIGN KEY (node_id) REFERENCES skill_nodes (node_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS rag_documents (
    document_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id    UUID,
    scope            VARCHAR(20) NOT NULL,
    source_type      VARCHAR(30) NOT NULL,
    file_name        TEXT NOT NULL,
    storage_url      TEXT,
    checksum         VARCHAR(64) NOT NULL,
    ingestion_status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    ingestion_version INT NOT NULL DEFAULT 1,
    error_message    TEXT,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_rag_documents_scope
        CHECK (scope IN ('GLOBAL', 'STUDENT')),
    CONSTRAINT ck_rag_documents_source_type
        CHECK (source_type IN ('ADMIN_KNOWLEDGE', 'TRANSCRIPT')),
    CONSTRAINT ck_rag_documents_status
        CHECK (ingestion_status IN ('PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT fk_rag_documents_owner
        FOREIGN KEY (owner_user_id) REFERENCES users (user_id) ON DELETE CASCADE
);

-- ============================================================
-- Recruitment processed cache
-- ============================================================

CREATE TABLE IF NOT EXISTS processed_companies (
    company_id   VARCHAR(255) PRIMARY KEY,
    company_link TEXT,
    logo         TEXT,
    name         TEXT,
    info         JSONB,
    contact      JSONB
);

CREATE TABLE IF NOT EXISTS processed_recruitments (
    recruitment_id       VARCHAR(255) PRIMARY KEY,
    recruitment_link     TEXT,
    basic_info           JSONB,
    descriptions         JSONB,
    application_deadline DATE
);

CREATE TABLE IF NOT EXISTS processed_recruitment_posts (
    post_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id     VARCHAR(255) NOT NULL,
    recruitment_id VARCHAR(255) NOT NULL,
    expired_at     DATE,
    CONSTRAINT uq_processed_recruitment_post UNIQUE (company_id, recruitment_id),
    CONSTRAINT fk_rp_company
        FOREIGN KEY (company_id) REFERENCES processed_companies (company_id) ON DELETE CASCADE,
    CONSTRAINT fk_rp_recruitment
        FOREIGN KEY (recruitment_id) REFERENCES processed_recruitments (recruitment_id) ON DELETE CASCADE
);

-- ============================================================
-- Auth / chat
-- ============================================================

CREATE TABLE IF NOT EXISTS oauth_accounts (
    oauth_acc_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL,
    provider_id   VARCHAR(255) NOT NULL,
    provider_name VARCHAR(255) NOT NULL,
    CONSTRAINT uq_oauth_provider UNIQUE (provider_name, provider_id),
    CONSTRAINT fk_oa_user
        FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL,
    token      TEXT NOT NULL UNIQUE,
    expired_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_rt_user
        FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS chat_sessions (
    session_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL,
    session_name VARCHAR(255),
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_cs_user
        FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS chat_messages (
    message_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL,
    role       VARCHAR(255) NOT NULL,
    content    TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_cm_session
        FOREIGN KEY (session_id) REFERENCES chat_sessions (session_id) ON DELETE CASCADE
);

-- ============================================================
-- Helpful indexes
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_students_career_id                ON students (career_id);
CREATE INDEX IF NOT EXISTS idx_students_university_id            ON students (university_id);
CREATE INDEX IF NOT EXISTS idx_students_portfolio_slug           ON students (portfolio_slug);
CREATE INDEX IF NOT EXISTS idx_crs_career_id                     ON career_required_skills (career_id);
CREATE INDEX IF NOT EXISTS idx_crs_skill_id                      ON career_required_skills (skill_id);
CREATE INDEX IF NOT EXISTS idx_skill_nodes_career_id             ON skill_nodes (career_id);
CREATE INDEX IF NOT EXISTS idx_skill_nodes_skill_id              ON skill_nodes (skill_id);
CREATE INDEX IF NOT EXISTS idx_skill_nodes_type_id               ON skill_nodes (type_id);
CREATE INDEX IF NOT EXISTS idx_skill_nodes_previous_node         ON skill_nodes (previous_node);
CREATE INDEX IF NOT EXISTS idx_roadmap_node_layouts_node_id      ON roadmap_node_layouts (node_id);
CREATE INDEX IF NOT EXISTS idx_skill_nodes_parent_node           ON skill_nodes (parent_node);
CREATE INDEX IF NOT EXISTS idx_student_skills_user_id            ON student_skills (user_id);
CREATE INDEX IF NOT EXISTS idx_student_skills_skill_id           ON student_skills (skill_id);
CREATE INDEX IF NOT EXISTS idx_student_progress_user_id          ON student_progress (user_id);
CREATE INDEX IF NOT EXISTS idx_student_progress_node_id          ON student_progress (node_id);
CREATE INDEX IF NOT EXISTS idx_portfolio_project_user_id         ON portfolio_project (user_id);
CREATE INDEX IF NOT EXISTS idx_portfolio_configs_user_id         ON portfolio_configs (user_id);
CREATE INDEX IF NOT EXISTS idx_student_education_user_id         ON student_education (user_id);
CREATE INDEX IF NOT EXISTS idx_feedback_sender_id                ON feedback (sender_id);
CREATE INDEX IF NOT EXISTS idx_feedback_receiver_id              ON feedback (receiver_id);
CREATE INDEX IF NOT EXISTS idx_feedback_attachment_feedback_id   ON feedback_attachment(feedback_id);
CREATE INDEX IF NOT EXISTS idx_prr_student_id                    ON portfolio_review_requests (student_id);
CREATE INDEX IF NOT EXISTS idx_skill_trends_skill_id             ON skill_trends (skill_id);
CREATE INDEX IF NOT EXISTS idx_sse_user_id                       ON student_skill_evidence (user_id);
CREATE INDEX IF NOT EXISTS idx_sse_student_skill_id              ON student_skill_evidence (student_skill_id);
CREATE INDEX IF NOT EXISTS idx_sse_node_id                       ON student_skill_evidence (node_id);
CREATE INDEX IF NOT EXISTS idx_rr_user_id                        ON roadmap_recommendations (user_id);
CREATE INDEX IF NOT EXISTS idx_rr_current_career_id              ON roadmap_recommendations (current_career_id);
CREATE INDEX IF NOT EXISTS idx_rr_recommend_career_id            ON roadmap_recommendations (recommend_career_id);
CREATE INDEX IF NOT EXISTS idx_rri_recommendation_id             ON roadmap_recommendation_items (recommendation_id);
CREATE INDEX IF NOT EXISTS idx_rri_node_id                       ON roadmap_recommendation_items (node_id);
CREATE INDEX IF NOT EXISTS idx_rag_documents_owner_source        ON rag_documents (owner_user_id, source_type);
CREATE INDEX IF NOT EXISTS idx_recruitment_posts_company_id      ON processed_recruitment_posts (company_id);
CREATE INDEX IF NOT EXISTS idx_recruitment_posts_recruitment_id  ON processed_recruitment_posts (recruitment_id);
CREATE INDEX IF NOT EXISTS idx_oauth_accounts_user_id            ON oauth_accounts (user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id            ON refresh_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_chat_sessions_user_id             ON chat_sessions (user_id);
CREATE INDEX IF NOT EXISTS idx_chat_messages_session_id          ON chat_messages (session_id);

