ALTER TABLE invoices
    ADD COLUMN export_job_id UUID REFERENCES export_jobs (id) ON DELETE SET NULL;

CREATE INDEX idx_invoices_export_job_id ON invoices (export_job_id);
