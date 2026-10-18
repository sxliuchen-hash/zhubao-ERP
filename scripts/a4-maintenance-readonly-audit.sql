-- A4 管理员改单与回收站只读核对；只输出聚合数量。
START TRANSACTION READ ONLY;

SELECT 'schema_migration_max' AS item, MAX(version) AS value FROM schema_migration;
SELECT 'mother_orders' AS item, COUNT(*) AS value FROM biz_mother_order
UNION ALL SELECT 'child_orders', COUNT(*) FROM biz_work_order
UNION ALL SELECT 'legacy_print_jobs', COUNT(*) FROM biz_print_job
UNION ALL SELECT 'target_print_jobs', COUNT(*) FROM biz_print_target_job
UNION ALL SELECT 'order_adjustments', COUNT(*) FROM biz_order_adjustment;
SELECT 'mother_recycled' AS item, COUNT(*) AS value FROM biz_mother_order WHERE recycled_at IS NOT NULL
UNION ALL SELECT 'child_recycled', COUNT(*) FROM biz_work_order WHERE recycled_at IS NOT NULL
UNION ALL SELECT 'legacy_print_recycled', COUNT(*) FROM biz_print_job WHERE recycled_at IS NOT NULL
UNION ALL SELECT 'target_print_recycled', COUNT(*) FROM biz_print_target_job WHERE recycled_at IS NOT NULL;
SELECT 'adjustment_target_type' AS item, target_type AS target, COUNT(*) AS value
  FROM biz_order_adjustment GROUP BY target_type;
SELECT 'mother_recycled_child_visible' AS item, COUNT(*) AS value
  FROM biz_work_order w JOIN biz_mother_order m ON m.code = w.mother_order_code
 WHERE m.recycled_at IS NOT NULL AND w.recycled_at IS NULL;
SELECT 'recycled_business_legacy_print_visible' AS item, COUNT(*) AS value
  FROM biz_print_job j
  LEFT JOIN biz_mother_order m ON m.code = j.business_code
  LEFT JOIN biz_work_order w ON w.code = j.business_code
 WHERE j.recycled_at IS NULL AND (m.recycled_at IS NOT NULL OR w.recycled_at IS NOT NULL);
SELECT 'recycled_business_target_print_visible' AS item, COUNT(*) AS value
  FROM biz_print_target_job j JOIN biz_print_batch b ON b.id = j.batch_id
  LEFT JOIN biz_mother_order m ON m.code = b.business_code
  LEFT JOIN biz_work_order w ON w.code = b.business_code
 WHERE j.recycled_at IS NULL AND (m.recycled_at IS NOT NULL OR w.recycled_at IS NOT NULL);
SELECT 'mother_adjustment_missing_target' AS item, COUNT(*) AS value
  FROM biz_order_adjustment a LEFT JOIN biz_mother_order m ON m.code = a.target_code
 WHERE a.target_type = 'MOTHER' AND m.code IS NULL;
SELECT 'child_adjustment_missing_target' AS item, COUNT(*) AS value
  FROM biz_order_adjustment a LEFT JOIN biz_work_order w ON w.code = a.target_code
 WHERE a.target_type = 'WORK_ORDER' AND w.code IS NULL;

COMMIT;
