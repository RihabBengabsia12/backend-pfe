-- 1. On ajoute la colonne d'abord
ALTER TABLE credential_account ADD COLUMN IF NOT EXISTS role VARCHAR(50);

-- 2. On nettoie au cas où
DELETE FROM credential_account WHERE email = 'admin@projectiq.com';

INSERT INTO credential_account (
    id,
    user_id,
    email,
    password_hash,
    account_status,
    role -- Ajoute la colonne ici
) VALUES (
             gen_random_uuid(),
             gen_random_uuid(),
             'admin@projectiq.com',
             '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.TVuHOnu',
             'ACTIVE',
             'ADMIN' -- Définis le rôle ici
         );