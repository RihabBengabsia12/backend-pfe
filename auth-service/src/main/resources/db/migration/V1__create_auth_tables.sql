CREATE TABLE credential_account (
                                    id UUID PRIMARY KEY,
                                    user_id UUID NOT NULL,
                                    email VARCHAR(255) UNIQUE NOT NULL,
                                    password_hash TEXT NOT NULL,
                                    account_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
                                    failed_login_count INT NOT NULL DEFAULT 0,
                                    last_login_at TIMESTAMPTZ NULL,
                                    password_updated_at TIMESTAMPTZ NULL,
                                    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                                    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE refresh_token (
                               id UUID PRIMARY KEY,
                               user_id UUID NOT NULL,
                               token_hash TEXT NOT NULL,
                               session_id UUID NOT NULL,
                               expires_at TIMESTAMPTZ NOT NULL,
                               revoked_at TIMESTAMPTZ NULL,
                               ip_address VARCHAR(100) NULL,
                               user_agent TEXT NULL,
                               created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE access_event (
                              id UUID PRIMARY KEY,
                              user_id UUID NULL,
                              email_attempted VARCHAR(255) NULL,
                              event_type VARCHAR(50) NOT NULL,
                              result VARCHAR(20) NOT NULL,
                              ip_address VARCHAR(100) NULL,
                              user_agent TEXT NULL,
                              occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);