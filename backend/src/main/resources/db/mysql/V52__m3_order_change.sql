-- 中途改单版本。已发生的交付和账单不在此表覆盖。
CREATE TABLE IF NOT EXISTS biz_order_change (
    id VARCHAR(40) NOT NULL,
    mother_order_code VARCHAR(90) NOT NULL,
    work_order_code VARCHAR(90) NOT NULL,
    old_requirement VARCHAR(500) NOT NULL,
    new_requirement VARCHAR(500) NOT NULL,
    old_due_date VARCHAR(40) NOT NULL,
    new_due_date VARCHAR(40) NOT NULL,
    status_code VARCHAR(32) NOT NULL,
    version_no INT NOT NULL,
    amount_unchanged BOOLEAN NOT NULL,
    customer_unit_price DECIMAL(18, 2) NOT NULL,
    worker_fee DECIMAL(18, 2) NOT NULL,
    finance_note VARCHAR(500) NOT NULL,
    confirmed_by VARCHAR(64) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    created_at VARCHAR(40) NOT NULL,
    updated_at VARCHAR(40) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_change_request (request_id)
);
