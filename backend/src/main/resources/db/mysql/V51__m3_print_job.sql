-- 服务端打印任务。SPOOLED 只表示已送入系统队列，不是已经出纸。
CREATE TABLE IF NOT EXISTS biz_print_job (
    id VARCHAR(40) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    business_code VARCHAR(90) NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    copies INT NOT NULL,
    content_snapshot VARCHAR(1000) NOT NULL,
    status_code VARCHAR(20) NOT NULL,
    station_id VARCHAR(64) NOT NULL,
    lease_until VARCHAR(40) NOT NULL,
    failure_reason VARCHAR(200) NOT NULL,
    predecessor_id VARCHAR(40) NOT NULL,
    retry_reason VARCHAR(200) NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    created_at VARCHAR(40) NOT NULL,
    updated_at VARCHAR(40) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_print_job_request (request_id)
);
