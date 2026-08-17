INSERT INTO role (code, label) VALUES
                                   ('ADMIN', 'Administrator'),
                                   ('ANALYST', 'Analyst'),
                                   ('MANAGER', 'Manager');

INSERT INTO permission (code, label, category) VALUES
                                                   ('USER_READ',         'Read users',           'USER'),
                                                   ('USER_CREATE',       'Create users',         'USER'),
                                                   ('USER_UPDATE',       'Update users',         'USER'),
                                                   ('USER_DISABLE',      'Disable users',        'USER'),
                                                   ('ROLE_READ',         'Read roles',           'ROLE'),
                                                   ('ROLE_ASSIGN',       'Assign roles',         'ROLE'),
                                                   ('AUDIT_ACCESS_READ', 'Read access audit',    'AUDIT'),
                                                   ('AUDIT_DATA_READ',   'Read data audit',      'AUDIT'),
                                                   ('PROFILE_READ',      'Read current profile', 'PROFILE');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN (
                                         'USER_READ',
                                         'USER_CREATE',
                                         'USER_UPDATE',
                                         'USER_DISABLE',
                                         'ROLE_READ',
                                         'ROLE_ASSIGN',
                                         'AUDIT_ACCESS_READ',
                                         'AUDIT_DATA_READ',
                                         'PROFILE_READ'
    )
WHERE r.code = 'ADMIN';

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('PROFILE_READ')
WHERE r.code = 'ANALYST';

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('PROFILE_READ')
WHERE r.code = 'MANAGER';