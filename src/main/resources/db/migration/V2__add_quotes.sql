-- ── Quotes ────────────────────────────────────────────────────────────────
CREATE TABLE quotes (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    quote_number VARCHAR(100) NOT NULL UNIQUE,
    status       VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
                              CHECK (status IN ('DRAFT','SENT','ACCEPTED','REJECTED','EXPIRED')),
    issue_date   DATE         NOT NULL,
    expiry_date  DATE         NOT NULL,

    client_id    UUID         REFERENCES clients (id) ON DELETE SET NULL,

    snap_company_name VARCHAR(255) NOT NULL DEFAULT '',
    snap_contact_name VARCHAR(255) NOT NULL DEFAULT '',
    snap_email        VARCHAR(255) NOT NULL DEFAULT '',
    snap_phone        VARCHAR(100) NOT NULL DEFAULT '',
    snap_address      TEXT         NOT NULL DEFAULT '',

    subtotal        NUMERIC(15,2) NOT NULL DEFAULT 0,
    discount_type   VARCHAR(10)   CHECK (discount_type IN ('AMOUNT','PERCENT')),
    discount_value  NUMERIC(15,2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    vat_enabled     BOOLEAN       NOT NULL DEFAULT FALSE,
    vat_rate        NUMERIC(6,4)  NOT NULL DEFAULT 0.15,
    vat_amount      NUMERIC(15,2) NOT NULL DEFAULT 0,
    total           NUMERIC(15,2) NOT NULL DEFAULT 0,

    notes           TEXT          NOT NULL DEFAULT '',

    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── Quote line items ───────────────────────────────────────────────────────
CREATE TABLE quote_line_items (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    quote_id    UUID          NOT NULL REFERENCES quotes (id) ON DELETE CASCADE,
    name        VARCHAR(500)  NOT NULL,
    description TEXT          NOT NULL DEFAULT '',
    quantity    NUMERIC(12,4) NOT NULL,
    unit_price  NUMERIC(15,2) NOT NULL,
    amount      NUMERIC(15,2) NOT NULL,
    sort_order  INT           NOT NULL DEFAULT 0
);

CREATE INDEX idx_quote_line_items_quote ON quote_line_items (quote_id);
CREATE INDEX idx_quotes_status          ON quotes (status);
CREATE INDEX idx_quotes_issue_date      ON quotes (issue_date);
CREATE INDEX idx_quotes_client_id       ON quotes (client_id);
