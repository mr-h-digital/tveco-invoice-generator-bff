CREATE TABLE export_job_documents (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    export_job_id      UUID NOT NULL REFERENCES export_jobs (id) ON DELETE CASCADE,
    uploaded_by_user_id UUID NOT NULL REFERENCES app_users (id) ON DELETE RESTRICT,
    original_name      VARCHAR(255) NOT NULL,
    mime_type          VARCHAR(255) NOT NULL,
    size_bytes         BIGINT NOT NULL,
    category           VARCHAR(32) NOT NULL,
    storage_provider   VARCHAR(32) NOT NULL,
    bucket_name        VARCHAR(255) NOT NULL,
    object_key         VARCHAR(1024) NOT NULL UNIQUE,
    etag               VARCHAR(255),
    checksum_sha256    VARCHAR(128),
    visible_to_client  BOOLEAN NOT NULL DEFAULT FALSE,
    status             VARCHAR(32) NOT NULL,
    completed_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_export_job_documents_export_job_id ON export_job_documents (export_job_id);
CREATE INDEX idx_export_job_documents_uploaded_by_user_id ON export_job_documents (uploaded_by_user_id);
CREATE INDEX idx_export_job_documents_status ON export_job_documents (status);

CREATE OR REPLACE FUNCTION set_export_job_documents_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_export_job_documents_updated_at
BEFORE UPDATE ON export_job_documents
FOR EACH ROW
EXECUTE FUNCTION set_export_job_documents_updated_at();