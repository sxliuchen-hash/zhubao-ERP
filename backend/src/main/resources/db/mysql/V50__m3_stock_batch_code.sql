-- 独立备料和公司自有批次。批次为空时维度键仍是原来的 8 段。
ALTER TABLE inv_stock_balance
    ADD COLUMN batch_code VARCHAR(64) NOT NULL DEFAULT '' AFTER stock_status;

ALTER TABLE inv_stock_ledger
    ADD COLUMN from_batch_code VARCHAR(64) NOT NULL DEFAULT '' AFTER to_stock_status,
    ADD COLUMN to_batch_code VARCHAR(64) NOT NULL DEFAULT '' AFTER from_batch_code,
    ADD COLUMN from_mother_order_code VARCHAR(90) NULL AFTER to_batch_code;
