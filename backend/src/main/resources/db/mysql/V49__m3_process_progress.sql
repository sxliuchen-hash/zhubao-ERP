-- 工序百分比进度。只追加进度事实，不改交付、质检、库存或工资。
CREATE TABLE IF NOT EXISTS biz_process_progress (
    order_code VARCHAR(90) NOT NULL,
    process_name VARCHAR(32) NOT NULL,
    percent_value INT NOT NULL,
    version_no INT NOT NULL,
    note_text VARCHAR(200) NOT NULL,
    operator_name VARCHAR(64) NOT NULL,
    updated_at VARCHAR(40) NOT NULL,
    PRIMARY KEY (order_code, process_name)
);

CREATE TABLE IF NOT EXISTS biz_process_progress_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_code VARCHAR(90) NOT NULL,
    process_name VARCHAR(32) NOT NULL,
    old_percent INT NOT NULL,
    new_percent INT NOT NULL,
    note_text VARCHAR(200) NOT NULL,
    reason_text VARCHAR(200) NOT NULL,
    operator_name VARCHAR(64) NOT NULL,
    created_at VARCHAR(40) NOT NULL,
    PRIMARY KEY (id)
);
