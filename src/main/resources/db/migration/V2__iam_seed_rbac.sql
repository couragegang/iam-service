-- Сиды ролей и прав (MVP). UUID фиксированы для ссылок из документации/тестов.

INSERT INTO permissions (id, key, description) VALUES
    ('10000000-0000-4000-8000-000000000001', 'iam.org.read',       'Просмотр карточки организации'),
    ('10000000-0000-4000-8000-000000000002', 'iam.org.update',     'Изменение организации'),
    ('10000000-0000-4000-8000-000000000003', 'iam.member.read',    'Просмотр членов'),
    ('10000000-0000-4000-8000-000000000004', 'iam.member.invite',  'Создание инвайтов'),
    ('10000000-0000-4000-8000-000000000005', 'iam.member.manage',  'Смена ролей / статуса / исключение'),
    ('10000000-0000-4000-8000-000000000006', 'iam.idp.read',       'Просмотр настроек IdP'),
    ('10000000-0000-4000-8000-000000000007', 'iam.idp.manage',     'Настройка IdP'),
    ('10000000-0000-4000-8000-000000000008', 'iam.audit.read',     'Просмотр аудита IAM')
ON CONFLICT (key) DO NOTHING;

INSERT INTO roles (id, key, description) VALUES
    ('20000000-0000-4000-8000-000000000001', 'owner',  'Владелец организации: полный доступ IAM'),
    ('20000000-0000-4000-8000-000000000002', 'admin',  'Администратор: управление членами и IdP'),
    ('20000000-0000-4000-8000-000000000003', 'member', 'Участник: базовые операции без админки'),
    ('20000000-0000-4000-8000-000000000004', 'viewer', 'Только чтение членов и org')
ON CONFLICT (key) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.key = 'owner'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.key IN (
    'iam.org.read', 'iam.org.update', 'iam.member.read', 'iam.member.invite',
    'iam.member.manage', 'iam.idp.read', 'iam.idp.manage', 'iam.audit.read'
)
WHERE r.key = 'admin'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.key IN ('iam.org.read', 'iam.member.read')
WHERE r.key = 'member'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.key IN ('iam.org.read', 'iam.member.read', 'iam.audit.read')
WHERE r.key = 'viewer'
ON CONFLICT DO NOTHING;
