CREATE TABLE app_user (
                          id         UUID PRIMARY KEY,
                          email      VARCHAR(255) UNIQUE NOT NULL,
                          full_name  VARCHAR(255) NOT NULL,
                          status     VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
                          created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                          updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE role (
                      id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                      code  VARCHAR(100) UNIQUE NOT NULL,
                      label VARCHAR(255) NOT NULL
);

CREATE TABLE permission (
                            id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            code     VARCHAR(100) UNIQUE NOT NULL,
                            label    VARCHAR(255) NOT NULL,
                            category VARCHAR(100) NOT NULL
);

CREATE TABLE user_role (
                           user_id UUID NOT NULL,
                           role_id UUID NOT NULL,
                           PRIMARY KEY (user_id, role_id)
);

CREATE TABLE role_permission (
                                 role_id       UUID NOT NULL,
                                 permission_id UUID NOT NULL,
                                 PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE data_event (
                            id            UUID PRIMARY KEY,
                            actor_user_id UUID NOT NULL,
                            entity_name   VARCHAR(100) NOT NULL,
                            entity_id     VARCHAR(100) NOT NULL,
                            action        VARCHAR(50) NOT NULL,
                            old_data      TEXT NULL,
                            new_data      TEXT NULL,
                            occurred_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);