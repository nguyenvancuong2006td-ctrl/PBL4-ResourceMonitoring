USE pbl4_monitor;

CREATE TABLE agent_registration_requests (
    registration_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    agent_id VARCHAR(50) NOT NULL UNIQUE,

    token_hash VARCHAR(255) NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    expires_at DATETIME NOT NULL,

    approved_at DATETIME NULL,

    credential_delivered_at DATETIME NULL,

    CONSTRAINT fk_registration_agent
        FOREIGN KEY (agent_id)
        REFERENCES client_agents(agent_id)
        ON DELETE CASCADE
);