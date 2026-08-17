ALTER TABLE credential_account
    ADD COLUMN IF NOT EXISTS reset_token_expires_at TIMESTAMPTZ NULL;
