-- 客户收款幂等键。空键允许多笔；非空键全局唯一。不修改已发布的 V36 和 V40。

ALTER TABLE fin_customer_receipt
    ADD COLUMN idempotency_key VARCHAR(80) NULL;

CREATE UNIQUE INDEX uk_fin_receipt_idempotency
    ON fin_customer_receipt (idempotency_key);
