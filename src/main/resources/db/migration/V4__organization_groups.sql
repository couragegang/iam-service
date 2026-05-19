-- Groups inside organization (ERD §2): default group per org, optional group-scoped invites.

CREATE TABLE organization_groups (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id          UUID NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    name            TEXT NOT NULL,
    slug            TEXT NOT NULL,
    is_default      BOOLEAN NOT NULL DEFAULT false,
    status          TEXT NOT NULL DEFAULT 'active'
                    CHECK (status IN ('active', 'archived')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (org_id, slug)
);

CREATE UNIQUE INDEX organization_groups_one_default_per_org
    ON organization_groups (org_id)
    WHERE is_default = true;

CREATE INDEX organization_groups_org_id_idx ON organization_groups (org_id);

ALTER TABLE organization_memberships
    ADD COLUMN access_scope TEXT NOT NULL DEFAULT 'org_wide'
        CHECK (access_scope IN ('org_wide', 'group_scoped'));

ALTER TABLE organization_invites
    ADD COLUMN group_id UUID REFERENCES organization_groups (id) ON DELETE CASCADE;

CREATE INDEX organization_invites_group_id_idx
    ON organization_invites (group_id)
    WHERE group_id IS NOT NULL;

CREATE TABLE group_memberships (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id          UUID NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    group_id        UUID NOT NULL REFERENCES organization_groups (id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status          TEXT NOT NULL DEFAULT 'active'
                    CHECK (status IN ('invited', 'active', 'suspended')),
    joined_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (group_id, user_id)
);

CREATE INDEX group_memberships_org_user_idx ON group_memberships (org_id, user_id);
CREATE INDEX group_memberships_group_id_idx ON group_memberships (group_id);

CREATE TABLE group_membership_roles (
    group_membership_id UUID NOT NULL REFERENCES group_memberships (id) ON DELETE CASCADE,
    role_id             UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    PRIMARY KEY (group_membership_id, role_id)
);

CREATE TABLE organization_invite_group_roles (
    invite_id   UUID NOT NULL REFERENCES organization_invites (id) ON DELETE CASCADE,
    role_id     UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    PRIMARY KEY (invite_id, role_id)
);

ALTER TABLE refresh_sessions
    ADD COLUMN group_id UUID REFERENCES organization_groups (id) ON DELETE SET NULL;

-- Backfill default group for existing organizations.
INSERT INTO organization_groups (org_id, name, slug, is_default, status)
SELECT o.id, o.name, 'default', true, 'active'
FROM organizations o
WHERE NOT EXISTS (
    SELECT 1 FROM organization_groups g WHERE g.org_id = o.id AND g.is_default = true
);

INSERT INTO permissions (id, key, description) VALUES
    ('10000000-0000-4000-8000-000000000009', 'iam.group.read',   'Просмотр групп организации'),
    ('10000000-0000-4000-8000-00000000000a', 'iam.group.manage', 'Создание и изменение групп')
ON CONFLICT (key) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.key IN ('iam.group.read', 'iam.group.manage')
WHERE r.key IN ('owner', 'admin')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.key = 'iam.group.read'
WHERE r.key IN ('member', 'viewer')
ON CONFLICT DO NOTHING;
