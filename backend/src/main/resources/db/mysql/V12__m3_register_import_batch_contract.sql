CREATE TABLE IF NOT EXISTS register_import_batch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    import_batch_no VARCHAR(96) NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    template_name VARCHAR(128) NOT NULL,
    source_file_name VARCHAR(255) NOT NULL,
    target_command VARCHAR(128) NOT NULL,
    total_rows INT NOT NULL DEFAULT 0,
    ready_rows INT NOT NULL DEFAULT 0,
    warning_rows INT NOT NULL DEFAULT 0,
    blocked_rows INT NOT NULL DEFAULT 0,
    executable BOOLEAN NOT NULL DEFAULT FALSE,
    batch_status VARCHAR(32) NOT NULL,
    confirmed_by VARCHAR(64) NOT NULL,
    confirmed_at VARCHAR(64) NOT NULL,
    confirm_remark VARCHAR(512) NOT NULL,
    next_action VARCHAR(255) NOT NULL,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_register_import_batch_no (import_batch_no),
    KEY idx_register_import_batch_template (template_code),
    KEY idx_register_import_batch_status (batch_status),
    KEY idx_register_import_batch_confirmed_at (confirmed_at)
);

CREATE TABLE IF NOT EXISTS register_import_row (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    import_batch_no VARCHAR(96) NOT NULL,
    source_line_no INT NOT NULL,
    source_register_sequence VARCHAR(64),
    row_status VARCHAR(32) NOT NULL,
    normalized_fields_json JSON NOT NULL,
    messages_json JSON NOT NULL,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_register_import_row_batch (import_batch_no),
    KEY idx_register_import_row_source (import_batch_no, source_line_no),
    CONSTRAINT fk_register_import_row_batch
        FOREIGN KEY (import_batch_no) REFERENCES register_import_batch(import_batch_no)
);
