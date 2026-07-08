-- ── Export jobs ───────────────────────────────────────────────────────────
CREATE TABLE export_jobs (
    id                      UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    job_number              VARCHAR(100)  NOT NULL UNIQUE,
    public_tracking_token   VARCHAR(100)  NOT NULL UNIQUE,
    client_id               UUID          REFERENCES clients (id) ON DELETE SET NULL,
    client_snapshot         TEXT          NOT NULL,
    destination_country     VARCHAR(255)  NOT NULL,
    vehicle_description     VARCHAR(500)  NOT NULL,
    source_channel          VARCHAR(50)   NOT NULL,
    project_value           NUMERIC(15,2) NOT NULL DEFAULT 0,
    status                  VARCHAR(30)   NOT NULL DEFAULT 'ENQUIRY',
    milestones              TEXT          NOT NULL,
    documents               TEXT          NOT NULL,
    payment_milestones      TEXT          NOT NULL,
    vault_documents         TEXT          NOT NULL,
    estimated_departure_date DATE        NOT NULL,
    estimated_arrival_date   DATE        NOT NULL,
    notes                   TEXT          NOT NULL DEFAULT '',
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_export_jobs_status ON export_jobs (status);
CREATE INDEX idx_export_jobs_created_at ON export_jobs (created_at DESC);

-- ── In-app notifications ─────────────────────────────────────────────────
CREATE TABLE app_notifications (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    title        VARCHAR(255) NOT NULL,
    message      TEXT         NOT NULL,
    read         BOOLEAN      NOT NULL DEFAULT FALSE,
    event_type   VARCHAR(80)  NOT NULL,
    reference_id VARCHAR(255),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_app_notifications_created_at ON app_notifications (created_at DESC);
CREATE INDEX idx_app_notifications_read ON app_notifications (read);

-- ── Email outbox ─────────────────────────────────────────────────────────
CREATE TABLE email_outbox (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient  VARCHAR(255) NOT NULL,
    subject    VARCHAR(500) NOT NULL,
    body       TEXT         NOT NULL,
    body_html  TEXT,
    status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts   INT          NOT NULL DEFAULT 0,
    sent_at    TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_email_outbox_status ON email_outbox (status);
CREATE INDEX idx_email_outbox_created_at ON email_outbox (created_at DESC);