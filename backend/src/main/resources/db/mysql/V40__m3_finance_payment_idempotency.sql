-- 应付付款幂等键。空键允许多笔；非空键全局唯一。不修改已发布的 V36。

ALTER TABLE fin_payable_payment
    ADD COLUMN idempotency_key VARCHAR(80) NULL;

CREATE UNIQUE INDEX uk_fin_payable_payment_idempotency
    ON fin_payable_payment (idempotency_key);
