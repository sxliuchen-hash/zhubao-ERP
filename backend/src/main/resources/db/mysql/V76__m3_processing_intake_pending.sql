-- 加工意图待核资料独立保存，不写库存；转正式单后保留关联与历史资料。
CREATE TABLE biz_processing_intake_pending (
    code VARCHAR(80) PRIMARY KEY,
    version INT NOT NULL,
    account_name VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL,
    mother_order_code VARCHAR(80) NOT NULL DEFAULT '',
    draft_json TEXT NOT NULL,
    created_at VARCHAR(40) NOT NULL,
    updated_at VARCHAR(40) NOT NULL,
    KEY idx_processing_pending_account (account_name, status, updated_at)
);
