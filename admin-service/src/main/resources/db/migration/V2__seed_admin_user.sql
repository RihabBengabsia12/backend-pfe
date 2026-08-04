INSERT INTO app_user (
    id,
    email,
    full_name,
    status
) VALUES (
             '11111111-1111-1111-1111-111111111111',
             'admin@projectiq.com',
             'System Administrator',
             'ACTIVE'
         );

INSERT INTO user_role (user_id, role_id)
SELECT
    '11111111-1111-1111-1111-111111111111',
    r.id
FROM role r
WHERE r.code = 'ADMIN';