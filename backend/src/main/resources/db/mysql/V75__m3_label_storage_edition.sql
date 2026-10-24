-- 标签保存要求换贴版本独立冻结，业务来源与旧打印任务不改写。
CREATE TABLE label_storage_edition (
    business_code VARCHAR(90) NOT NULL,
    edition INT NOT NULL,
    request_id VARCHAR(80) NOT NULL,
    content_snapshot TEXT NOT NULL,
    PRIMARY KEY (business_code, edition),
    UNIQUE KEY uk_label_storage_request (request_id)
);
