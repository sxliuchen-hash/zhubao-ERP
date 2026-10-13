-- 改单财务差额。减额走红字，增额用独立来源键，工费只追加调整。
ALTER TABLE biz_order_change
    ADD COLUMN customer_delta DECIMAL(18, 2) NOT NULL DEFAULT 0 AFTER updated_at,
    ADD COLUMN worker_delta DECIMAL(18, 2) NOT NULL DEFAULT 0 AFTER customer_delta,
    ADD COLUMN receivable_code VARCHAR(40) NOT NULL DEFAULT '' AFTER worker_delta,
    ADD COLUMN process_name VARCHAR(32) NOT NULL DEFAULT '' AFTER receivable_code,
    ADD COLUMN worker_name VARCHAR(64) NOT NULL DEFAULT '' AFTER process_name;
