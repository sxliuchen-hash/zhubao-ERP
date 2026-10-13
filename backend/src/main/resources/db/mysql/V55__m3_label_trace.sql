-- 标签快照改为长文本，并记录同版补打、换版、出纸核实和实物换贴。
ALTER TABLE biz_print_job MODIFY content_snapshot TEXT NOT NULL;

CREATE TABLE IF NOT EXISTS biz_label_trace (
    id VARCHAR(40) NOT NULL,
    mother_order_code VARCHAR(90) NOT NULL,
    business_code VARCHAR(90) NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    version_no VARCHAR(20) NOT NULL,
    instance_no VARCHAR(40) NOT NULL,
    print_job_id VARCHAR(40) NOT NULL,
    reason_text VARCHAR(200) NOT NULL,
    operator_name VARCHAR(64) NOT NULL,
    created_at VARCHAR(40) NOT NULL,
    PRIMARY KEY (id)
);
