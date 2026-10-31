USE pbl4_monitor;

ALTER TABLE agent_registration_requests
ADD COLUMN credential_ciphertext TEXT NULL
    AFTER token_hash,
ADD COLUMN credential_expires_at DATETIME NULL
    AFTER credential_delivered_at;