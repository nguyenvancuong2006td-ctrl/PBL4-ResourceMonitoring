CREATE DATABASE IF NOT EXISTS pbl4_monitor;
USE pbl4_monitor;

-- 0. XÓA CÁC BẢNG CŨ THEO THỨ TỰ ĐỂ KHÔNG LỖI KHÓA NGOẠI (FOREIGN KEY)
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS process_info, network_metrics, disk_metrics, memory_metrics, cpu_metrics;
DROP TABLE IF EXISTS monitoring_snapshots, network_interface_info, disk_info, memory_info, cpu_info;
DROP TABLE IF EXISTS remote_commands, alerts, alert_rules;
DROP TABLE IF EXISTS system_logs, audit_logs, system_configs, role_permissions, permissions, accounts, roles, verification_codes;
DROP TABLE IF EXISTS agent_credentials, client_agents, clients;
DROP TABLE IF EXISTS server_service_status, server_metrics, servers;
SET FOREIGN_KEY_CHECKS = 1;

-- =======================================================
-- CỤM 1: QUẢN TRỊ & TÀI KHOẢN (ACCOUNT, ROLE, PERMISSION)
-- =======================================================
CREATE TABLE roles (
    role_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL,
    description TEXT
);

CREATE TABLE permissions (
    permission_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    permission_code VARCHAR(50) NOT NULL,
    description TEXT
);

CREATE TABLE role_permissions (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES roles(role_id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permissions(permission_id) ON DELETE CASCADE
);

CREATE TABLE accounts (
    account_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    email_verified BOOLEAN DEFAULT FALSE,
    status VARCHAR(20) DEFAULT 'PENDING',
    create_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_login_at DATETIME,
    FOREIGN KEY (role_id) REFERENCES roles(role_id)
);

CREATE TABLE verification_codes (
    verification_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    verification_type VARCHAR(20) NOT NULL,
    target VARCHAR(255) NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME NOT NULL,
    verified_at DATETIME,
    attempt_count INT NOT NULL DEFAULT 0,
    resend_count INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (account_id) REFERENCES accounts(account_id) ON DELETE CASCADE
);

CREATE TABLE system_configs (
    config_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(20) NOT NULL DEFAULT 'SYSTEM',
    collection_interval_seconds INT NOT NULL DEFAULT 10,
    heartbeat_interval_seconds INT NOT NULL DEFAULT 5,
    heartbeat_timeout_seconds INT NOT NULL DEFAULT 15,
    updated_by BIGINT,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE (config_key),
    FOREIGN KEY (updated_by) REFERENCES accounts(account_id) ON DELETE SET NULL
);

CREATE TABLE audit_logs (
    log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50),
    entity_id VARCHAR(50),
    description TEXT,
    ip_address VARCHAR(50),
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (account_id) REFERENCES accounts(account_id) ON DELETE SET NULL
);

-- =======================================================
-- CỤM 2: LÕI THIẾT BỊ (CLIENT, AGENT, CREDENTIAL)
-- =======================================================
CREATE TABLE clients (
    client_id VARCHAR(50) PRIMARY KEY,
    hostname VARCHAR(100),
    ip_address VARCHAR(50),
    os_name VARCHAR(50),
    os_version VARCHAR(50),
    kernel_version VARCHAR(50),
    status VARCHAR(20),
    last_seen_at DATETIME,
    boot_time DATETIME,
    created_at DATETIME,
    updated_at DATETIME,
    management_note TEXT
);

CREATE TABLE client_agents (
    agent_id VARCHAR(50) PRIMARY KEY,
    client_id VARCHAR(50) UNIQUE NOT NULL,
    version VARCHAR(20),
    status VARCHAR(20),
    registered_at DATETIME,
    last_connected_at DATETIME,
    last_heartbeat_at DATETIME,
    disconnected_at DATETIME,
    FOREIGN KEY (client_id) REFERENCES clients(client_id) ON DELETE CASCADE
);

CREATE TABLE agent_credentials (
    credential_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id VARCHAR(50) UNIQUE NOT NULL,
    credential_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20),
    issued_at DATETIME,
    expires_at DATETIME,
    last_used_at DATETIME,
    FOREIGN KEY (agent_id) REFERENCES client_agents(agent_id) ON DELETE CASCADE
);

CREATE TABLE system_logs (
    log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT,
    client_id VARCHAR(50),
    agent_id VARCHAR(50),
    event_type VARCHAR(50),
    level VARCHAR(20),
    source VARCHAR(50),
    description TEXT,
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (account_id) REFERENCES accounts(account_id) ON DELETE SET NULL,
    FOREIGN KEY (client_id) REFERENCES clients(client_id) ON DELETE SET NULL,
    FOREIGN KEY (agent_id) REFERENCES client_agents(agent_id) ON DELETE SET NULL
);

-- =======================================================
-- CỤM 3: CẢNH BÁO VÀ ĐIỀU KHIỂN (ALERTS, COMMANDS)
-- =======================================================
CREATE TABLE alert_rules (
    rule_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100),
    resource_type VARCHAR(50),
    operator VARCHAR(20),
    threshold_value DOUBLE,
    duration_seconds INT,
    severity VARCHAR(20),
    enabled BOOLEAN,
    created_at DATETIME,
    updated_at DATETIME
);

CREATE TABLE alerts (
    alert_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_id BIGINT NOT NULL,
    client_id VARCHAR(50) NOT NULL,
    account_id BIGINT,
    resource_type VARCHAR(50),
    current_value DOUBLE,
    threshold_value DOUBLE,
    severity VARCHAR(20),
    status VARCHAR(20),
    message TEXT,
    created_at DATETIME,
    acknowledged_at DATETIME,
    resolved_at DATETIME,
    FOREIGN KEY (rule_id) REFERENCES alert_rules(rule_id) ON DELETE CASCADE,
    FOREIGN KEY (client_id) REFERENCES clients(client_id) ON DELETE CASCADE,
    FOREIGN KEY (account_id) REFERENCES accounts(account_id) ON DELETE SET NULL
);

CREATE TABLE remote_commands (
    command_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    client_id VARCHAR(50) NOT NULL,
    command_type VARCHAR(50),
    status VARCHAR(20),
    result TEXT,
    error_message TEXT,
    sent_at DATETIME,
    started_at DATETIME,
    completed_at DATETIME,
    FOREIGN KEY (account_id) REFERENCES accounts(account_id) ON DELETE CASCADE,
    FOREIGN KEY (client_id) REFERENCES clients(client_id) ON DELETE CASCADE
);

-- =======================================================
-- CỤM 4: THÔNG SỐ PHẦN CỨNG TĨNH (HARDWARE INFO)
-- =======================================================
CREATE TABLE cpu_info (
    cpu_info_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_id VARCHAR(50) UNIQUE NOT NULL,
    model_name VARCHAR(100),
    manufacturer VARCHAR(50),
    core_count INT,
    thread_count INT,
    frequency_mhz DOUBLE,
    FOREIGN KEY (client_id) REFERENCES clients(client_id) ON DELETE CASCADE
);

CREATE TABLE memory_info (
    memory_info_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_id VARCHAR(50) UNIQUE NOT NULL,
    total_bytes BIGINT,
    FOREIGN KEY (client_id) REFERENCES clients(client_id) ON DELETE CASCADE
);

CREATE TABLE disk_info (
    disk_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_id VARCHAR(50) NOT NULL,
    device_name VARCHAR(50),
    mount_point VARCHAR(100),
    file_system VARCHAR(50),
    total_bytes BIGINT,
    UNIQUE (client_id, device_name, mount_point),
    FOREIGN KEY (client_id) REFERENCES clients(client_id) ON DELETE CASCADE
);

CREATE TABLE network_interface_info (
    interface_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_id VARCHAR(50) NOT NULL,
    interface_name VARCHAR(50),
    ip_address VARCHAR(50),
    mac_address VARCHAR(50),
    status VARCHAR(20),
    description TEXT,
    UNIQUE (client_id, interface_name),
    FOREIGN KEY (client_id) REFERENCES clients(client_id) ON DELETE CASCADE
);

-- =======================================================
-- CỤM 5: DỮ LIỆU GIÁM SÁT ĐỘNG (SNAPSHOT & METRICS)
-- =======================================================
CREATE TABLE monitoring_snapshots (
    snapshot_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_id VARCHAR(50) NOT NULL,
    collected_at DATETIME,
    received_at DATETIME,
    INDEX idx_snapshot_client_time (client_id, collected_at),
    FOREIGN KEY (client_id) REFERENCES clients(client_id) ON DELETE CASCADE
);

CREATE TABLE cpu_metrics (
    metric_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    snapshot_id BIGINT UNIQUE NOT NULL,
    usage_percent DOUBLE,
    load_average_1m DOUBLE,
    FOREIGN KEY (snapshot_id) REFERENCES monitoring_snapshots(snapshot_id) ON DELETE CASCADE
);

CREATE TABLE memory_metrics (
    metric_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    snapshot_id BIGINT UNIQUE NOT NULL,
    total_bytes BIGINT,
    used_bytes BIGINT,
    available_bytes BIGINT,
    usage_percent DOUBLE,
    FOREIGN KEY (snapshot_id) REFERENCES monitoring_snapshots(snapshot_id) ON DELETE CASCADE
);

CREATE TABLE disk_metrics (
    metric_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    snapshot_id BIGINT NOT NULL,
    disk_id BIGINT NOT NULL,
    total_bytes BIGINT,
    used_bytes BIGINT,
    free_bytes BIGINT,
    usage_percent DOUBLE,
    FOREIGN KEY (snapshot_id) REFERENCES monitoring_snapshots(snapshot_id) ON DELETE CASCADE,
    FOREIGN KEY (disk_id) REFERENCES disk_info(disk_id) ON DELETE CASCADE
);

CREATE TABLE network_metrics (
    metric_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    snapshot_id BIGINT NOT NULL,
    interface_id BIGINT NOT NULL,
    received_bytes BIGINT,
    transmitted_bytes BIGINT,
    download_speed DOUBLE,
    upload_speed DOUBLE,
    FOREIGN KEY (snapshot_id) REFERENCES monitoring_snapshots(snapshot_id) ON DELETE CASCADE,
    FOREIGN KEY (interface_id) REFERENCES network_interface_info(interface_id) ON DELETE CASCADE
);

CREATE TABLE process_info (
    pid BIGINT,
    snapshot_id BIGINT NOT NULL,
    name VARCHAR(100),
    cpu_usage DOUBLE,
    memory_usage DOUBLE,
    status VARCHAR(20),
    PRIMARY KEY (pid, snapshot_id),
    FOREIGN KEY (snapshot_id) REFERENCES monitoring_snapshots(snapshot_id) ON DELETE CASCADE
);

-- =======================================================
-- CỤM 6: MÁY CHỦ ĐỘC LẬP (SERVER SELF-MONITORING)
-- =======================================================
CREATE TABLE servers (
    server_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hostname VARCHAR(100),
    ip_address VARCHAR(50),
    os_name VARCHAR(50),
    os_version VARCHAR(50),
    status VARCHAR(20),
    last_seen_at DATETIME,
    created_at DATETIME
);

CREATE TABLE server_metrics (
    metric_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    server_id BIGINT NOT NULL,
    cpu_usage DOUBLE,
    ram_total_bytes BIGINT,
    ram_used_bytes BIGINT,
    ram_usage_percent DOUBLE,
    disk_total_bytes BIGINT,
    disk_used_bytes BIGINT,
    disk_usage_percent DOUBLE,
    received_bytes BIGINT,
    transmitted_bytes BIGINT,
    download_speed DOUBLE,
    upload_speed DOUBLE,
    collected_at DATETIME,
    FOREIGN KEY (server_id) REFERENCES servers(server_id) ON DELETE CASCADE
);

CREATE TABLE server_service_status (
    service_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    server_id BIGINT NOT NULL,
    service_name VARCHAR(100),
    status VARCHAR(20),
    checked_at DATETIME,
    error_message TEXT,
    FOREIGN KEY (server_id) REFERENCES servers(server_id) ON DELETE CASCADE
);