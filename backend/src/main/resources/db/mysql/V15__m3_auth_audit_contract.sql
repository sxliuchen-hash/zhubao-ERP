CREATE TABLE IF NOT EXISTS auth_audit_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    username VARCHAR(64),
    operator VARCHAR(128),
    role VARCHAR(64),
    result VARCHAR(32) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    occurred_at VARCHAR(64) NOT NULL,
    session_token_suffix VARCHAR(16),
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_auth_audit_event_id (event_id),
    KEY idx_auth_audit_username_time (username, created_time),
    KEY idx_auth_audit_result_time (result, created_time)
);
