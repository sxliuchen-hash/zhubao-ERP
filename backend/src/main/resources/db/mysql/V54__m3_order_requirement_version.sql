-- 改单生效后的有效要求、路线和必要执行轮次。不覆盖原母单、子单和已有交付。
ALTER TABLE biz_order_change
    ADD COLUMN old_route VARCHAR(200) NOT NULL DEFAULT '' AFTER worker_name,
    ADD COLUMN new_route VARCHAR(200) NOT NULL DEFAULT '' AFTER old_route,
    ADD COLUMN expected_version INT NOT NULL DEFAULT 0 AFTER new_route,
    ADD COLUMN rework_process VARCHAR(32) NOT NULL DEFAULT '' AFTER expected_version;

CREATE TABLE IF NOT EXISTS biz_order_requirement_version (
    id VARCHAR(40) NOT NULL,
    mother_order_code VARCHAR(90) NOT NULL,
    work_order_code VARCHAR(90) NOT NULL,
    version_no INT NOT NULL,
    requirement_text VARCHAR(500) NOT NULL,
    due_date VARCHAR(40) NOT NULL,
    route_text VARCHAR(200) NOT NULL,
    change_id VARCHAR(40) NOT NULL,
    effective_at VARCHAR(40) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_requirement_target_version (mother_order_code, work_order_code, version_no)
);

CREATE TABLE IF NOT EXISTS biz_execution_round (
    id VARCHAR(40) NOT NULL,
    mother_order_code VARCHAR(90) NOT NULL,
    source_work_order_code VARCHAR(90) NOT NULL,
    round_work_order_code VARCHAR(90) NOT NULL,
    process_name VARCHAR(32) NOT NULL,
    change_id VARCHAR(40) NOT NULL,
    supersedes_order_code VARCHAR(90) NOT NULL,
    created_at VARCHAR(40) NOT NULL,
    PRIMARY KEY (id)
);
