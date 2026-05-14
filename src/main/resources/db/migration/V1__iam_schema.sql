-- IAM core schema (PostgreSQL 14+; gen_random_uuid() встроен)
-- Кодировка кластера UTF-8; миграции в UTF-8.

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           TEXT NOT NULL,
    email_verified_at TIMESTAMPTZ,
    status          TEXT NOT NULL DEFAULT 'active'
                    CHECK (status IN ('active', 'suspended', 'deleted')),
    display_name    TEXT,
    locale          TEXT NOT NULL DEFAULT 'ru',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ
);

CREATE UNIQUE INDEX users_email_active_lower
    ON users (lower(email))
    WHERE deleted_at IS NULL;

CREATE TABLE user_passwords (
    user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    password_hash   TEXT NOT NULL,
    must_change     BOOLEAN NOT NULL DEFAULT false,
    rotated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id)
);

CREATE TABLE user_external_identities (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider        TEXT NOT NULL,
    subject         TEXT NOT NULL,
    email_at_link   TEXT,
    profile_json    JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, subject)
);

CREATE INDEX user_external_identities_user_id_idx
    ON user_external_identities (user_id);

CREATE TABLE organizations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT NOT NULL,
    slug            TEXT NOT NULL,
    plan_tier       TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX organizations_slug_lower
    ON organizations (lower(slug));

CREATE TABLE organization_memberships (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    org_id          UUID NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    status          TEXT NOT NULL DEFAULT 'active'
                    CHECK (status IN ('invited', 'active', 'suspended')),
    joined_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, org_id)
);

CREATE INDEX organization_memberships_org_id_idx
    ON organization_memberships (org_id);
CREATE INDEX organization_memberships_user_id_idx
    ON organization_memberships (user_id);

CREATE TABLE roles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key             TEXT NOT NULL UNIQUE,
    description     TEXT NOT NULL DEFAULT ''
);

CREATE TABLE permissions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key             TEXT NOT NULL UNIQUE,
    description     TEXT NOT NULL DEFAULT ''
);

CREATE TABLE role_permissions (
    role_id         UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_id   UUID NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE membership_roles (
    membership_id   UUID NOT NULL REFERENCES organization_memberships (id) ON DELETE CASCADE,
    role_id         UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    PRIMARY KEY (membership_id, role_id)
);

CREATE TABLE refresh_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    org_id          UUID REFERENCES organizations (id) ON DELETE SET NULL,
    jti             TEXT NOT NULL UNIQUE,
    family_id       UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    ip_inet         INET,
    user_agent_hash BYTEA,
    device_label    TEXT
);

CREATE INDEX refresh_sessions_user_active_idx
    ON refresh_sessions (user_id)
    WHERE revoked_at IS NULL;

CREATE INDEX refresh_sessions_family_idx
    ON refresh_sessions (family_id);

CREATE TABLE email_verification_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash      TEXT NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX email_verification_tokens_user_idx
    ON email_verification_tokens (user_id);

CREATE TABLE password_reset_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash      TEXT NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX password_reset_tokens_user_idx
    ON password_reset_tokens (user_id);

CREATE TABLE organization_invites (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id              UUID NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    email               TEXT NOT NULL,
    token_hash          TEXT NOT NULL,
    expires_at          TIMESTAMPTZ NOT NULL,
    accepted_at         TIMESTAMPTZ,
    revoked_at          TIMESTAMPTZ,
    created_by_membership_id UUID REFERENCES organization_memberships (id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX organization_invites_org_email_idx
    ON organization_invites (org_id, lower(email))
    WHERE revoked_at IS NULL AND accepted_at IS NULL;

CREATE TABLE organization_invite_roles (
    invite_id       UUID NOT NULL REFERENCES organization_invites (id) ON DELETE CASCADE,
    role_id         UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    PRIMARY KEY (invite_id, role_id)
);

CREATE TABLE org_idp_configs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id              UUID NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    type                TEXT NOT NULL CHECK (type IN ('oidc', 'saml')),
    issuer              TEXT,
    client_id           TEXT,
    client_secret_enc   TEXT,
    metadata_url        TEXT,
    metadata_json       JSONB,
    enabled             BOOLEAN NOT NULL DEFAULT false,
    jit_provisioning    BOOLEAN NOT NULL DEFAULT false,
    default_role_id     UUID REFERENCES roles (id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (org_id)
);

CREATE TABLE iam_audit_events (
    id              BIGSERIAL PRIMARY KEY,
    ts              TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor_user_id   UUID REFERENCES users (id) ON DELETE SET NULL,
    org_id          UUID REFERENCES organizations (id) ON DELETE SET NULL,
    action          TEXT NOT NULL,
    target_type     TEXT,
    target_id       TEXT,
    ip_inet         INET,
    payload         JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX iam_audit_events_org_ts_idx
    ON iam_audit_events (org_id, ts DESC);

CREATE INDEX iam_audit_events_actor_ts_idx
    ON iam_audit_events (actor_user_id, ts DESC);

CREATE TABLE login_attempts (
    id              BIGSERIAL PRIMARY KEY,
    ts              TIMESTAMPTZ NOT NULL DEFAULT now(),
    email_lower     TEXT,
    ip_inet         INET,
    success         BOOLEAN NOT NULL
);

CREATE INDEX login_attempts_email_ts_idx
    ON login_attempts (email_lower, ts DESC);

CREATE INDEX login_attempts_ip_ts_idx
    ON login_attempts (ip_inet, ts DESC);
