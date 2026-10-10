-- 母单拆分决定、子单和贴纸补打日志。不拆仍用母单号；拆开生成子单并记下工序名称。
-- 默认母单、子单模板补上验证码、客户、加工要求、期限和单号。物料、数量、重量仍可留在模板里。

CREATE TABLE IF NOT EXISTS biz_mother_order_split (
    mother_order_code VARCHAR(80) NOT NULL,
    split_decision VARCHAR(20) NOT NULL,
    verification_code VARCHAR(16) NOT NULL DEFAULT '',
    operator VARCHAR(80) NOT NULL,
    created_at VARCHAR(40) NOT NULL,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (mother_order_code),
    CONSTRAINT fk_mother_order_split_mother
        FOREIGN KEY (mother_order_code) REFERENCES biz_mother_order(code)
);

CREATE TABLE IF NOT EXISTS biz_work_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(90) NOT NULL,
    mother_order_code VARCHAR(80) NOT NULL,
    customer_id BIGINT,
    customer_name VARCHAR(120) NOT NULL DEFAULT '',
    processing_requirement VARCHAR(500) NOT NULL DEFAULT '',
    due_date VARCHAR(20) NOT NULL DEFAULT '',
    material_code VARCHAR(80) NOT NULL,
    goods_name VARCHAR(160) NOT NULL DEFAULT '',
    quantity DECIMAL(18,3) NOT NULL,
    quantity_unit VARCHAR(20) NOT NULL DEFAULT '件',
    weight DECIMAL(18,3) NOT NULL,
    warehouse_code VARCHAR(50) NOT NULL,
    process_names VARCHAR(80) NOT NULL,
    verification_code VARCHAR(16) NOT NULL,
    ledger_code VARCHAR(80) NOT NULL,
    operator VARCHAR(80) NOT NULL,
    created_at VARCHAR(40) NOT NULL,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_work_order_code (code),
    UNIQUE KEY uk_work_order_verification (verification_code),
    KEY idx_work_order_mother (mother_order_code),
    CONSTRAINT fk_work_order_mother
        FOREIGN KEY (mother_order_code) REFERENCES biz_mother_order(code)
);

CREATE TABLE IF NOT EXISTS biz_label_reprint_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    label_type VARCHAR(20) NOT NULL,
    business_code VARCHAR(90) NOT NULL,
    verification_code VARCHAR(16) NOT NULL,
    operator VARCHAR(80) NOT NULL,
    reason VARCHAR(200) NOT NULL,
    reprinted_at VARCHAR(40) NOT NULL,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_label_reprint_business (label_type, business_code)
);

UPDATE sys_label_template
SET fields_json = '[{"label":"验证码","source":"verificationCode","sortOrder":1},{"label":"客户","source":"customer","sortOrder":2},{"label":"加工要求","source":"processingRequirement","sortOrder":3},{"label":"期限","source":"dueDate","sortOrder":4},{"label":"单号","source":"code","sortOrder":5},{"label":"物料","source":"goodsName","sortOrder":6},{"label":"数量","source":"quantity","sortOrder":7},{"label":"重量","source":"weight","sortOrder":8}]'
WHERE template_code = 'MOTHER-DEFAULT';

UPDATE sys_label_template
SET fields_json = '[{"label":"验证码","source":"verificationCode","sortOrder":1},{"label":"客户","source":"customer","sortOrder":2},{"label":"加工要求","source":"processingRequirement","sortOrder":3},{"label":"期限","source":"dueDate","sortOrder":4},{"label":"单号","source":"code","sortOrder":5},{"label":"物料","source":"goodsName","sortOrder":6},{"label":"数量","source":"quantity","sortOrder":7},{"label":"重量","source":"weight","sortOrder":8}]'
WHERE template_code = 'WORK_ORDER-DEFAULT';
