-- 启用交货后结单。不补历史金额，不改已有应收、工资和收付款。
UPDATE fin_settlement_policy
   SET enabled = 'Y', enabled_at = '2026-10-27T00:00:00', operator_name = 'migration'
 WHERE policy_code = 'POST_DELIVERY';

INSERT INTO fin_settlement_policy (policy_code, enabled, enabled_at, operator_name)
SELECT 'POST_DELIVERY_ON', 'Y', '2026-10-27T00:00:00', 'migration'
WHERE NOT EXISTS (
    SELECT 1 FROM fin_settlement_policy WHERE policy_code = 'POST_DELIVERY_ON'
);
