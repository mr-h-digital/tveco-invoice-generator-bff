-- ── Export inquiries (client requests before formal quote/job) ─────────────────
CREATE TABLE export_inquiries (
    id                      UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    inquiry_number          VARCHAR(100)  NOT NULL UNIQUE,
    client_id               UUID          NOT NULL REFERENCES clients (id) ON DELETE CASCADE,
    inquiry_type            VARCHAR(20)   NOT NULL CHECK (inquiry_type IN ('INQUIRY','REQUEST')),
    status                  VARCHAR(40)   NOT NULL CHECK (status IN ('SUBMITTED','UNDER_REVIEW','WAITING_ON_CLIENT','READY_FOR_QUOTE','QUOTED','CONVERTED_TO_JOB','CLOSED')),
    source_channel          VARCHAR(50)   NOT NULL,
    destination_country     VARCHAR(255)  NOT NULL,
    vehicle_description     VARCHAR(500)  NOT NULL,
    project_value           NUMERIC(15,2) NOT NULL DEFAULT 0,
    estimated_departure_date DATE,
    estimated_arrival_date   DATE,
    notes                   TEXT          NOT NULL DEFAULT '',
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_export_inquiries_client_id ON export_inquiries (client_id);
CREATE INDEX idx_export_inquiries_status ON export_inquiries (status);
CREATE INDEX idx_export_inquiries_created_at ON export_inquiries (created_at DESC);

CREATE TABLE export_inquiry_messages (
    id                       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    inquiry_id               UUID         NOT NULL REFERENCES export_inquiries (id) ON DELETE CASCADE,
    sender_role              VARCHAR(20)  NOT NULL CHECK (sender_role IN ('ADMIN','CLIENT')),
    sender_email             VARCHAR(255) NOT NULL,
    message                  TEXT         NOT NULL,
    requires_client_response BOOLEAN      NOT NULL DEFAULT FALSE,
    client_responded         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_export_inquiry_messages_inquiry_id ON export_inquiry_messages (inquiry_id, created_at ASC);

-- ── Link quotes/jobs to inquiry lifecycle ──────────────────────────────────────
ALTER TABLE quotes
    ADD COLUMN inquiry_id UUID REFERENCES export_inquiries (id) ON DELETE SET NULL,
    ADD COLUMN client_decision_at TIMESTAMPTZ,
    ADD COLUMN client_decision_note TEXT;

CREATE INDEX idx_quotes_inquiry_id ON quotes (inquiry_id);

ALTER TABLE export_jobs
    ADD COLUMN quote_id UUID REFERENCES quotes (id) ON DELETE SET NULL,
    ADD COLUMN inquiry_id UUID REFERENCES export_inquiries (id) ON DELETE SET NULL;

CREATE INDEX idx_export_jobs_quote_id ON export_jobs (quote_id);
CREATE INDEX idx_export_jobs_inquiry_id ON export_jobs (inquiry_id);
