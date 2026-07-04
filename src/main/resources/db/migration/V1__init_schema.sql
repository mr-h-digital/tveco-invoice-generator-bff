-- ── Clients ────────────────────────────────────────────────────────────────
CREATE TABLE clients (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    company_name VARCHAR(255) NOT NULL,
    contact_name VARCHAR(255) NOT NULL DEFAULT '',
    email        VARCHAR(255) NOT NULL DEFAULT '',
    phone        VARCHAR(100) NOT NULL DEFAULT '',
    address      TEXT         NOT NULL DEFAULT '',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_clients_email ON clients (email) WHERE email <> '';

-- ── Invoices ───────────────────────────────────────────────────────────────
CREATE TABLE invoices (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_number VARCHAR(100) NOT NULL UNIQUE,
    status         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
                                CHECK (status IN ('DRAFT','SENT','PAID','OVERDUE')),
    issue_date     DATE         NOT NULL,
    due_date       DATE         NOT NULL,

    -- Optional FK to clients table (snapshot is always stored below)
    client_id      UUID         REFERENCES clients (id) ON DELETE SET NULL,

    -- Snapshot of client details at invoice time
    snap_company_name VARCHAR(255) NOT NULL DEFAULT '',
    snap_contact_name VARCHAR(255) NOT NULL DEFAULT '',
    snap_email        VARCHAR(255) NOT NULL DEFAULT '',
    snap_phone        VARCHAR(100) NOT NULL DEFAULT '',
    snap_address      TEXT         NOT NULL DEFAULT '',

    -- Totals
    subtotal       NUMERIC(15,2) NOT NULL DEFAULT 0,
    discount_type  VARCHAR(10)   CHECK (discount_type IN ('AMOUNT','PERCENT')),
    discount_value NUMERIC(15,2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    vat_enabled    BOOLEAN       NOT NULL DEFAULT FALSE,
    vat_rate       NUMERIC(6,4)  NOT NULL DEFAULT 0.15,
    vat_amount     NUMERIC(15,2) NOT NULL DEFAULT 0,
    total          NUMERIC(15,2) NOT NULL DEFAULT 0,

    notes          TEXT          NOT NULL DEFAULT '',

    -- Payment details
    pay_bank           VARCHAR(255) NOT NULL DEFAULT '',
    pay_account_name   VARCHAR(255) NOT NULL DEFAULT '',
    pay_account_number VARCHAR(100) NOT NULL DEFAULT '',
    pay_account_type   VARCHAR(100) NOT NULL DEFAULT '',
    pay_branch_code    VARCHAR(50)  NOT NULL DEFAULT '',
    pay_reference      VARCHAR(255) NOT NULL DEFAULT '',

    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── Line items ─────────────────────────────────────────────────────────────
CREATE TABLE line_items (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id  UUID          NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    name        VARCHAR(500)  NOT NULL,
    description TEXT          NOT NULL DEFAULT '',
    quantity    NUMERIC(12,4) NOT NULL,
    unit_price  NUMERIC(15,2) NOT NULL,
    amount      NUMERIC(15,2) NOT NULL,
    sort_order  INT           NOT NULL DEFAULT 0
);

CREATE INDEX idx_line_items_invoice ON line_items (invoice_id);
CREATE INDEX idx_invoices_status    ON invoices (status);
CREATE INDEX idx_invoices_issue_date ON invoices (issue_date);
CREATE INDEX idx_invoices_client_id  ON invoices (client_id);
