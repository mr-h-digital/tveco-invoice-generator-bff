-- ── App users ──────────────────────────────────────────────────────────────
CREATE TABLE app_users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(50)  NOT NULL DEFAULT 'admin',
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ── Refresh token sessions (persistent rotation/revocation) ────────────────
CREATE TABLE refresh_token_sessions (
    token_id    VARCHAR(64) PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN     NOT NULL DEFAULT FALSE,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_token_sessions_user_id ON refresh_token_sessions (user_id);
CREATE INDEX idx_refresh_token_sessions_expires_at ON refresh_token_sessions (expires_at);
