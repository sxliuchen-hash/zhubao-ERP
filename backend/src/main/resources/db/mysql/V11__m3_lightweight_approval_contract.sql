CREATE TABLE IF NOT EXISTS approval_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    approval_code VARCHAR(64) NOT NULL,
    scene VARCHAR(64) NOT NULL,
    business_code VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    applicant VARCHAR(64) NOT NULL,
    approver VARCHAR(64) NOT NULL,
    owner_role VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    source_status VARCHAR(64) NOT NULL,
    source_summary TEXT NOT NULL,
    evidence_code VARCHAR(128),
    formal_payroll BOOLEAN NOT NULL DEFAULT FALSE,
    created_at VARCHAR(64) NOT NULL,
    decided_at VARCHAR(64),
    decision_operator VARCHAR(64),
    decision_comment VARCHAR(512),
    locked BOOLEAN NOT NULL DEFAULT FALSE,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_approval_task_code (approval_code),
    KEY idx_approval_task_status (status),
    KEY idx_approval_task_scene_business (scene, business_code)
);

CREATE TABLE IF NOT EXISTS approval_audit_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    approval_code VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    operator VARCHAR(64) NOT NULL,
    comment VARCHAR(512),
    evidence_code VARCHAR(128),
    occurred_at VARCHAR(64) NOT NULL,
    locked BOOLEAN NOT NULL DEFAULT FALSE,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_approval_audit_code (approval_code)
);
