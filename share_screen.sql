USE pbl4_monitor;

CREATE TABLE screen_sharing_sessions (
    session_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    session_code CHAR(36) NOT NULL UNIQUE,

    account_id BIGINT NOT NULL,
    client_id VARCHAR(50) NOT NULL,
    agent_id VARCHAR(50) NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',

    requested_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    started_at DATETIME NULL,
    ended_at DATETIME NULL,
    last_heartbeat_at DATETIME NULL,

    end_reason VARCHAR(255) NULL,
    error_message TEXT NULL,

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_screen_session_account
        FOREIGN KEY (account_id)
        REFERENCES accounts(account_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_screen_session_client
        FOREIGN KEY (client_id)
        REFERENCES clients(client_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_screen_session_agent
        FOREIGN KEY (agent_id)
        REFERENCES client_agents(agent_id)
        ON DELETE CASCADE,

    INDEX idx_screen_session_account_status
        (account_id, status),

    INDEX idx_screen_session_client_status
        (client_id, status),

    INDEX idx_screen_session_agent_status
        (agent_id, status)
);