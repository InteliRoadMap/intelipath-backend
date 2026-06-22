CREATE TABLE IF NOT EXISTS rag_documents (
    document_id UUID PRIMARY KEY,
    owner_user_id UUID NULL,
    scope VARCHAR(20) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    file_name TEXT NOT NULL,
    storage_url TEXT NULL,
    checksum VARCHAR(64) NOT NULL,
    ingestion_status VARCHAR(20) NOT NULL,
    ingestion_version INTEGER NOT NULL DEFAULT 1,
    error_message TEXT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_rag_documents_owner
        FOREIGN KEY (owner_user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT ck_rag_documents_scope
        CHECK (scope IN ('GLOBAL', 'STUDENT')),
    CONSTRAINT ck_rag_documents_source_type
        CHECK (source_type IN ('ADMIN_KNOWLEDGE', 'TRANSCRIPT')),
    CONSTRAINT ck_rag_documents_status
        CHECK (ingestion_status IN ('PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_rag_documents_owner_source
    ON rag_documents (owner_user_id, source_type);
