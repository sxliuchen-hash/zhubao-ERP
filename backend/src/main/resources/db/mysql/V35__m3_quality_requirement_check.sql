-- 质检对照加工要求。库存数量仍走台账，这里只留核对记录和返工关联。

CREATE TABLE IF NOT EXISTS biz_quality_requirement_check (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    quality_case_code VARCHAR(120) NOT NULL,
    order_code VARCHAR(120) NOT NULL,
    mother_order_code VARCHAR(120) NOT NULL,
    inspector VARCHAR(80) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    frozen TINYINT NOT NULL DEFAULT 0,
    ledger_code VARCHAR(80) NOT NULL DEFAULT '',
    rework_order_code VARCHAR(120) NOT NULL DEFAULT '',
    created_at VARCHAR(40) NOT NULL,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_quality_requirement_case (quality_case_code),
    UNIQUE KEY uk_quality_requirement_order (order_code)
);

CREATE TABLE IF NOT EXISTS biz_quality_requirement_line (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    quality_case_code VARCHAR(120) NOT NULL,
    line_no INT NOT NULL,
    clause_text VARCHAR(500) NOT NULL,
    verdict VARCHAR(20) NOT NULL,
    reason VARCHAR(500) NOT NULL DEFAULT '',
    UNIQUE KEY uk_quality_requirement_line (quality_case_code, line_no)
);
