-- A3 明细事实核对；只读事务，不更改运行库。
START TRANSACTION READ ONLY;

SELECT 'schema_migration_max' AS item, MAX(version) AS value FROM schema_migration;
SELECT 'biz_mother_order' AS table_name, COUNT(*) AS row_count FROM biz_mother_order
UNION ALL SELECT 'biz_mother_order_item', COUNT(*) FROM biz_mother_order_item
UNION ALL SELECT 'biz_receipt_line', COUNT(*) FROM biz_receipt_line
UNION ALL SELECT 'biz_work_order', COUNT(*) FROM biz_work_order
UNION ALL SELECT 'biz_child_line_allocation', COUNT(*) FROM biz_child_line_allocation
UNION ALL SELECT 'biz_outbound_order', COUNT(*) FROM biz_outbound_order
UNION ALL SELECT 'biz_outbound_order_item', COUNT(*) FROM biz_outbound_order_item
UNION ALL SELECT 'inv_stock_ledger', COUNT(*) FROM inv_stock_ledger
UNION ALL SELECT 'inv_stock_balance', COUNT(*) FROM inv_stock_balance;

SELECT 'mother_without_old_item' AS item, COUNT(*) AS value
  FROM biz_mother_order m LEFT JOIN biz_mother_order_item i ON i.mother_order_code = m.code
 WHERE i.id IS NULL;
SELECT 'old_item_count_not_one' AS item, COUNT(*) AS value
  FROM (SELECT m.code, COUNT(i.id) AS item_count
          FROM biz_mother_order m LEFT JOIN biz_mother_order_item i ON i.mother_order_code = m.code
         GROUP BY m.code) grouped
 WHERE item_count <> 1;
SELECT 'legacy_old_item_parent_mismatch' AS item, COUNT(*) AS value
  FROM biz_mother_order_item i JOIN biz_mother_order m ON m.code = i.mother_order_code
 WHERE (m.source_register_type IS NULL OR m.source_register_type NOT LIKE 'INTAKE_LINES:%')
   AND (i.material_code <> m.material_code OR i.quantity <> m.quantity OR i.weight <> m.weight
    OR i.quantity_unit <> m.quantity_unit OR i.ledger_code <> m.ledger_code);
SELECT 'detailed_old_item_first_line_mismatch' AS item, COUNT(*) AS value
  FROM biz_mother_order_item i JOIN biz_mother_order m ON m.code = i.mother_order_code
  LEFT JOIN biz_receipt_line r ON r.mother_order_code = m.code AND r.line_no = 1
 WHERE m.source_register_type LIKE 'INTAKE_LINES:%'
   AND (r.line_no IS NULL OR i.material_code <> r.material_code
    OR i.quantity <> r.quantity OR i.weight <> r.weight
    OR i.quantity_unit <> r.quantity_unit OR i.ledger_code <> r.ledger_code);
SELECT 'mother_ledger_missing' AS item, COUNT(*) AS value
  FROM biz_mother_order m LEFT JOIN inv_stock_ledger l ON l.code = m.ledger_code
 WHERE l.code IS NULL;
SELECT 'old_item_ledger_missing' AS item, COUNT(*) AS value
  FROM biz_mother_order_item i LEFT JOIN inv_stock_ledger l ON l.code = i.ledger_code
 WHERE l.code IS NULL;
SELECT 'legacy_mother_ledger_amount_mismatch' AS item, COUNT(*) AS value
  FROM biz_mother_order m JOIN inv_stock_ledger l ON l.code = m.ledger_code
 WHERE (m.source_register_type IS NULL OR m.source_register_type NOT LIKE 'INTAKE_LINES:%')
   AND (l.quantity <> m.quantity OR l.weight <> m.weight
    OR l.mother_order_code <> m.code OR l.material_code <> m.material_code);
SELECT 'detailed_mother_first_ledger_mismatch' AS item, COUNT(*) AS value
  FROM biz_mother_order m LEFT JOIN biz_receipt_line r
    ON r.mother_order_code = m.code AND r.line_no = 1
 WHERE m.source_register_type LIKE 'INTAKE_LINES:%'
   AND (r.line_no IS NULL OR m.ledger_code <> r.ledger_code OR m.material_code <> r.material_code);
SELECT 'detailed_receipt_sum_mismatch' AS item, COUNT(*) AS value
  FROM biz_mother_order m LEFT JOIN (
      SELECT mother_order_code, SUM(quantity) AS quantity, SUM(weight) AS weight
        FROM biz_receipt_line GROUP BY mother_order_code
  ) receipt_totals ON receipt_totals.mother_order_code = m.code
 WHERE m.source_register_type LIKE 'INTAKE_LINES:%'
   AND (receipt_totals.mother_order_code IS NULL OR receipt_totals.quantity <> m.quantity
    OR receipt_totals.weight <> m.weight);

SELECT 'receipt_line_parent_missing' AS item, COUNT(*) AS value
  FROM biz_receipt_line r LEFT JOIN biz_mother_order m ON m.code = r.mother_order_code
 WHERE m.code IS NULL;
SELECT 'receipt_line_ledger_missing' AS item, COUNT(*) AS value
  FROM biz_receipt_line r LEFT JOIN inv_stock_ledger l ON l.code = r.ledger_code
 WHERE l.code IS NULL;
SELECT 'receipt_line_ledger_amount_mismatch' AS item, COUNT(*) AS value
  FROM biz_receipt_line r JOIN inv_stock_ledger l ON l.code = r.ledger_code
 WHERE l.quantity <> r.quantity OR l.weight <> r.weight
    OR l.mother_order_code <> r.mother_order_code OR l.material_code <> r.material_code;
SELECT 'allocation_source_missing' AS item, COUNT(*) AS value
  FROM biz_child_line_allocation a LEFT JOIN biz_receipt_line r
    ON r.mother_order_code = a.mother_order_code AND r.line_no = a.source_line_no
 WHERE r.mother_order_code IS NULL;
SELECT 'allocation_child_sum_mismatch' AS item, COUNT(*) AS value
  FROM biz_work_order w JOIN biz_mother_order m ON m.code = w.mother_order_code
  LEFT JOIN (SELECT child_order_code, SUM(quantity) AS quantity, SUM(weight) AS weight
               FROM biz_child_line_allocation GROUP BY child_order_code) a ON a.child_order_code = w.code
 WHERE m.source_register_type LIKE 'INTAKE_LINES:%'
   AND (a.child_order_code IS NULL OR a.quantity <> w.quantity OR a.weight <> w.weight);
SELECT 'allocation_ledger_amount_mismatch' AS item, COUNT(*) AS value
  FROM biz_child_line_allocation a JOIN inv_stock_ledger l ON l.code = a.ledger_code
 WHERE l.quantity <> a.quantity OR l.weight <> a.weight
    OR l.mother_order_code <> a.mother_order_code OR l.work_order_code <> a.child_order_code
    OR l.material_code <> a.material_code;
SELECT 'work_order_ledger_missing' AS item, COUNT(*) AS value
  FROM biz_work_order w LEFT JOIN inv_stock_ledger l ON l.code = w.ledger_code
 WHERE l.code IS NULL;
SELECT 'work_order_parent_missing' AS item, COUNT(*) AS value
  FROM biz_work_order w LEFT JOIN biz_mother_order m ON m.code = w.mother_order_code
 WHERE m.code IS NULL;
SELECT 'work_order_ledger_amount_mismatch' AS item, COUNT(*) AS value
  FROM biz_work_order w JOIN inv_stock_ledger l ON l.code = w.ledger_code
 WHERE l.quantity <> w.quantity OR l.weight <> w.weight
    OR l.mother_order_code <> w.mother_order_code OR l.work_order_code <> w.code
    OR l.material_code <> w.material_code;
SELECT 'child_sum_exceeds_mother' AS item, COUNT(*) AS value
  FROM (SELECT mother_order_code, SUM(quantity) AS quantity, SUM(weight) AS weight
          FROM biz_work_order GROUP BY mother_order_code) children
  JOIN biz_mother_order m ON m.code = children.mother_order_code
 WHERE children.quantity > m.quantity OR children.weight > m.weight;
SELECT 'detailed_child_without_allocation' AS item, COUNT(*) AS value
  FROM biz_work_order w JOIN biz_mother_order m ON m.code = w.mother_order_code
  LEFT JOIN biz_child_line_allocation a ON a.child_order_code = w.code
 WHERE m.source_register_type LIKE 'INTAKE_LINES:%' AND a.child_order_code IS NULL;
SELECT 'legacy_child_without_allocation' AS item, COUNT(*) AS value
  FROM biz_work_order w JOIN biz_mother_order m ON m.code = w.mother_order_code
  LEFT JOIN biz_child_line_allocation a ON a.child_order_code = w.code
 WHERE (m.source_register_type IS NULL OR m.source_register_type NOT LIKE 'INTAKE_LINES:%')
   AND a.child_order_code IS NULL;

SELECT 'outbound_without_old_item' AS item, COUNT(*) AS value
  FROM biz_outbound_order o LEFT JOIN biz_outbound_order_item i ON i.outbound_order_code = o.code
 WHERE i.id IS NULL;
SELECT 'outbound_old_item_count_not_one' AS item, COUNT(*) AS value
  FROM (SELECT o.code, COUNT(i.id) AS item_count
          FROM biz_outbound_order o LEFT JOIN biz_outbound_order_item i ON i.outbound_order_code = o.code
         GROUP BY o.code) grouped
 WHERE item_count <> 1;
SELECT 'outbound_old_item_parent_mismatch' AS item, COUNT(*) AS value
  FROM biz_outbound_order_item i JOIN biz_outbound_order o ON o.code = i.outbound_order_code
 WHERE i.mother_order_code <> o.mother_order_code OR i.work_order_code <> o.work_order_code
    OR i.material_code <> o.material_code OR i.quantity <> o.delivery_quantity
    OR i.quantity_unit <> o.delivery_quantity_unit OR i.weight <> o.delivery_weight
    OR i.ledger_code <> o.ledger_code;
SELECT 'outbound_old_item_ledger_missing' AS item, COUNT(*) AS value
  FROM biz_outbound_order_item i LEFT JOIN inv_stock_ledger l ON l.code = i.ledger_code
 WHERE l.code IS NULL;
SELECT 'outbound_ledger_missing' AS item, COUNT(*) AS value
  FROM biz_outbound_order o LEFT JOIN inv_stock_ledger l ON l.code = o.ledger_code
 WHERE l.code IS NULL;
SELECT 'outbound_ledger_amount_mismatch' AS item, COUNT(*) AS value
  FROM biz_outbound_order o JOIN inv_stock_ledger l ON l.code = o.ledger_code
 WHERE l.quantity <> o.delivery_quantity OR l.weight <> o.delivery_weight
    OR l.mother_order_code <> o.mother_order_code OR l.work_order_code <> o.work_order_code
    OR l.material_code <> o.material_code;
SELECT 'split_decision' AS item, split_decision AS target, COUNT(*) AS value
  FROM biz_mother_order_split GROUP BY split_decision;
SELECT 'ledger_document_type' AS item, document_type AS target, COUNT(*) AS value
  FROM inv_stock_ledger GROUP BY document_type;

COMMIT;
