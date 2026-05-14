-- Хэш refresh-токена (сырой токен только клиенту; в БД — SHA-256 hex).
ALTER TABLE refresh_sessions ADD COLUMN IF NOT EXISTS refresh_token_hash TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS refresh_sessions_active_rt_hash_key
    ON refresh_sessions (refresh_token_hash)
    WHERE revoked_at IS NULL AND refresh_token_hash IS NOT NULL;

-- OIDC state для CSRF-защиты (start → callback).
CREATE TABLE IF NOT EXISTS oauth_oidc_states (
    state               TEXT PRIMARY KEY,
    provider            TEXT NOT NULL,
    redirect_after      TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at          TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS oauth_oidc_states_expires_idx ON oauth_oidc_states (expires_at);
