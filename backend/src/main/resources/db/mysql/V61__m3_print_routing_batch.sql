-- 工位凭据仅保存 SHA-256 摘要；Windows 队列名称只在厂内程序本机配置。
CREATE TABLE IF NOT EXISTS biz_print_station (
    station_id VARCHAR(64) NOT NULL PRIMARY KEY,
    station_name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL,
    remark VARCHAR(255) NOT NULL,
    token_hash CHAR(64) NOT NULL
);
CREATE TABLE IF NOT EXISTS biz_print_queue (
    station_id VARCHAR(64) NOT NULL,
    queue_id VARCHAR(64) NOT NULL,
    queue_name VARCHAR(100) NOT NULL,
    label_type VARCHAR(30) NOT NULL,
    width_mm INT NOT NULL,
    height_mm INT NOT NULL,
    enabled BOOLEAN NOT NULL,
    default_queue BOOLEAN NOT NULL,
    PRIMARY KEY (station_id, queue_id),
    CONSTRAINT fk_print_queue_station FOREIGN KEY (station_id) REFERENCES biz_print_station (station_id)
);
CREATE TABLE IF NOT EXISTS biz_print_batch (
    id VARCHAR(40) NOT NULL PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL,
    business_code VARCHAR(90) NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    copies_per_target INT NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    content_snapshot LONGTEXT NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    created_at VARCHAR(40) NOT NULL,
    UNIQUE KEY uk_print_batch_request (request_id)
);
CREATE TABLE IF NOT EXISTS biz_print_target_job (
    id VARCHAR(40) NOT NULL PRIMARY KEY,
    batch_id VARCHAR(40) NOT NULL,
    target_station_id VARCHAR(64) NOT NULL,
    target_queue_id VARCHAR(64) NOT NULL,
    copies INT NOT NULL,
    status_code VARCHAR(20) NOT NULL,
    claimed_station_id VARCHAR(64) NOT NULL,
    lease_until VARCHAR(40) NOT NULL,
    failure_reason VARCHAR(255) NOT NULL,
    paper_check_result VARCHAR(255) NOT NULL,
    predecessor_id VARCHAR(40) NOT NULL,
    retry_reason VARCHAR(255) NOT NULL,
    created_at VARCHAR(40) NOT NULL,
    updated_at VARCHAR(40) NOT NULL,
    UNIQUE KEY uk_print_target (batch_id, target_station_id, target_queue_id),
    KEY ix_print_target_claim (target_station_id, status_code, created_at),
    CONSTRAINT fk_print_target_batch FOREIGN KEY (batch_id) REFERENCES biz_print_batch (id)
);

-- 旧任务没有目标工位和队列，不允许新版工位盲领；保留原快照供人工核对。
UPDATE biz_print_job SET status_code = 'UNCERTAIN',
    failure_reason = '旧版任务未指定工位和队列，请核纸后重新建立批次'
WHERE status_code IN ('PENDING', 'LEASED');
