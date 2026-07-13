CREATE TABLE password_reset_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    user_id    UUID NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens (user_id);
CREATE INDEX idx_password_reset_tokens_expires_at ON password_reset_tokens (expires_at);

CREATE TABLE auth_recovery_otp_challenges (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    challenge_id VARCHAR(64) NOT NULL UNIQUE,
    user_id      UUID REFERENCES app_users (id) ON DELETE CASCADE,
    purpose      VARCHAR(32) NOT NULL,
    channel      VARCHAR(32) NOT NULL,
    identifier   VARCHAR(255) NOT NULL,
    otp_hash     VARCHAR(128) NOT NULL,
    attempts     INT NOT NULL DEFAULT 0,
    expires_at   TIMESTAMPTZ NOT NULL,
    consumed_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_auth_recovery_otp_challenges_user_id ON auth_recovery_otp_challenges (user_id);
CREATE INDEX idx_auth_recovery_otp_challenges_expires_at ON auth_recovery_otp_challenges (expires_at);