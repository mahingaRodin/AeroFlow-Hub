ALTER TABLE users
    ADD COLUMN mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN totp_secret_encrypted TEXT,
    ADD COLUMN pending_totp_secret_encrypted TEXT,
    ADD COLUMN pending_totp_expires_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN pending_totp_failed_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN totp_last_used_step BIGINT NOT NULL DEFAULT -1;

CREATE TABLE mfa_login_challenges (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_mfa_login_challenge_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_mfa_login_challenge_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_mfa_login_challenge_expiry ON mfa_login_challenges (expires_at);

CREATE TABLE mfa_recovery_codes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_mfa_recovery_code_hash UNIQUE (code_hash),
    CONSTRAINT fk_mfa_recovery_code_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_mfa_recovery_code_user ON mfa_recovery_codes (user_id);
