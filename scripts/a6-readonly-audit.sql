-- A6 财务、库存与证据对账。只读事务，不改运行库，不建索引或约束。
-- 适配测试服 V63：不读取 V64 的 source_ledger_code，也不把收货冲正当成测试服已有事实。
-- 只输出计数和差异类型，不输出客户、单号、金额或附件名。
-- 差异只有两类：missing = 业务动作在，但应同时存在的台账、账单或证据不在；
-- orphan = 台账、账单或证据在，但没有对应业务来源。
-- 差额、退货、返工、回收和冲正是追加事实，不要求覆盖或删除原来的台账与账单。
-- 财务入账、工资确认是后补动作；没有入账动作时，不把“尚未生成账单”计成缺失。
-- 证据可后补。未关联附件只要没有声明一个不存在的单据，就不计孤立。

START TRANSACTION READ ONLY;

SELECT 'probe' AS kind, 'schema_migration_max_version' AS item, MAX(version) AS value
  FROM schema_migration;
SELECT 'probe' AS kind, 'v64_source_ledger_column' AS item, COUNT(*) AS value
  FROM information_schema.columns
 WHERE table_schema = DATABASE()
   AND table_name = 'inv_stock_ledger'
   AND column_name = 'source_ledger_code';

SELECT 'population' AS kind, 'biz_mother_order' AS item, COUNT(*) AS value FROM biz_mother_order
UNION ALL SELECT 'population', 'biz_mother_recycled', COUNT(*) FROM biz_mother_order WHERE recycled_at IS NOT NULL
UNION ALL SELECT 'population', 'biz_work_order', COUNT(*) FROM biz_work_order
UNION ALL SELECT 'population', 'biz_work_recycled', COUNT(*) FROM biz_work_order WHERE recycled_at IS NOT NULL
UNION ALL SELECT 'population', 'biz_receipt_line', COUNT(*) FROM biz_receipt_line
UNION ALL SELECT 'population', 'biz_child_line_allocation', COUNT(*) FROM biz_child_line_allocation
UNION ALL SELECT 'population', 'biz_outbound_order', COUNT(*) FROM biz_outbound_order
UNION ALL SELECT 'population', 'biz_process_station_handoff', COUNT(*) FROM biz_process_station_handoff
UNION ALL SELECT 'population', 'biz_quality_requirement_check', COUNT(*) FROM biz_quality_requirement_check
UNION ALL SELECT 'population', 'biz_quality_round', COUNT(*) FROM biz_quality_round
UNION ALL SELECT 'population', 'biz_quality_rework_report', COUNT(*) FROM biz_quality_rework_report
UNION ALL SELECT 'population', 'biz_order_change', COUNT(*) FROM biz_order_change
UNION ALL SELECT 'population', 'biz_order_change_applied', COUNT(*) FROM biz_order_change WHERE status_code = 'APPLIED'
UNION ALL SELECT 'population', 'biz_execution_round', COUNT(*) FROM biz_execution_round
UNION ALL SELECT 'population', 'inv_stock_ledger', COUNT(*) FROM inv_stock_ledger
UNION ALL SELECT 'population', 'inv_stock_balance', COUNT(*) FROM inv_stock_balance
UNION ALL SELECT 'population', 'fin_receivable_line', COUNT(*) FROM fin_receivable_line
UNION ALL SELECT 'population', 'fin_customer_receipt', COUNT(*) FROM fin_customer_receipt
UNION ALL SELECT 'population', 'fin_receipt_allocation', COUNT(*) FROM fin_receipt_allocation
UNION ALL SELECT 'population', 'fin_payroll_line', COUNT(*) FROM fin_payroll_line
UNION ALL SELECT 'population', 'fin_payroll_run_confirmed', COUNT(*) FROM fin_payroll_run WHERE confirmed = 1
UNION ALL SELECT 'population', 'fin_payable', COUNT(*) FROM fin_payable
UNION ALL SELECT 'population', 'fin_payable_payment', COUNT(*) FROM fin_payable_payment
UNION ALL SELECT 'population', 'fin_consumable_item', COUNT(*) FROM fin_consumable_item
UNION ALL SELECT 'population', 'fin_consumable_movement', COUNT(*) FROM fin_consumable_movement
UNION ALL SELECT 'population', 'fin_fixed_asset', COUNT(*) FROM fin_fixed_asset
UNION ALL SELECT 'population', 'fin_depreciation_entry', COUNT(*) FROM fin_depreciation_entry
UNION ALL SELECT 'population', 'doc_evidence_attachment', COUNT(*) FROM doc_evidence_attachment
UNION ALL SELECT 'population', 'doc_business_evidence_link', COUNT(*) FROM doc_business_evidence_link
UNION ALL SELECT 'population', 'approval_task', COUNT(*) FROM approval_task;

SELECT 'context' AS kind, 'ledger_type' AS item, document_type AS detail, COUNT(*) AS value
  FROM inv_stock_ledger GROUP BY document_type;
SELECT 'context' AS kind, 'receivable_source_type' AS item, source_type AS detail, COUNT(*) AS value
  FROM fin_receivable_line GROUP BY source_type;
SELECT 'context' AS kind, 'payable_kind' AS item, kind AS detail, COUNT(*) AS value
  FROM fin_payable GROUP BY kind;
SELECT 'context' AS kind, 'evidence_object_type' AS item, IFNULL(object_type, '') AS detail, COUNT(*) AS value
  FROM doc_evidence_attachment GROUP BY object_type;
SELECT 'context' AS kind, 'evidence_link_operation' AS item, operation_type AS detail, COUNT(*) AS value
  FROM doc_business_evidence_link GROUP BY operation_type;

SELECT 'context' AS kind, 'additional_customer_return' AS item, COUNT(*) AS value
  FROM inv_stock_ledger WHERE document_type = 'customer_return'
UNION ALL SELECT 'context', 'additional_after_sales_review', COUNT(*) FROM inv_stock_ledger WHERE document_type = 'after_sales_review'
UNION ALL SELECT 'context', 'additional_rework_dispatch', COUNT(*) FROM inv_stock_ledger WHERE document_type = 'rework_dispatch'
UNION ALL SELECT 'context', 'additional_rework_report', COUNT(*) FROM inv_stock_ledger WHERE document_type = 'rework_report'
UNION ALL SELECT 'context', 'additional_quality_freeze', COUNT(*) FROM inv_stock_ledger WHERE document_type = 'quality_freeze'
UNION ALL SELECT 'context', 'additional_prep_or_company', COUNT(*) FROM inv_stock_ledger
  WHERE document_type IN ('prep_receipt', 'prep_issue', 'prep_return', 'company_receipt', 'company_issue', 'company_return')
UNION ALL SELECT 'context', 'additional_inbound_reversal', COUNT(*) FROM inv_stock_ledger WHERE document_type = 'inbound_reversal'
UNION ALL SELECT 'context', 'additional_receivable_change', COUNT(*) FROM fin_receivable_line WHERE source_type IN ('CHANGE', 'RED')
UNION ALL SELECT 'context', 'additional_payroll_adjustment', COUNT(*) FROM fin_payroll_line WHERE source_key LIKE 'PAY-ADJ|%'
UNION ALL SELECT 'context', 'evidence_without_link', COUNT(*)
  FROM doc_evidence_attachment a
  LEFT JOIN doc_business_evidence_link l ON l.attachment_code = a.code
 WHERE l.id IS NULL;
SELECT 'context' AS kind, 'attachment_status_kind' AS item,
       CASE HEX(status)
         WHEN 'E5B7B2E4B88AE4BCA0' THEN 'uploaded'
         WHEN 'E5B7B2E585B3E88194' THEN 'linked_status'
         WHEN 'E5B7B2E5BD92E6A1A3' THEN 'archived'
         WHEN 'E5B7B2E4B88BE7BABF' THEN 'retired'
         WHEN 'E5BE85E6A0B8E5AFB9' THEN 'pending_check'
         ELSE 'other_status'
       END AS detail,
       COUNT(*) AS value
  FROM doc_evidence_attachment
 GROUP BY detail;

-- 业务单据写了台账编号，或质检冻结声明了库存动作，但台账不在。
SELECT 'missing' AS kind, 'mother_ledger' AS item, COUNT(*) AS value
  FROM biz_mother_order m
  LEFT JOIN inv_stock_ledger l ON l.code = m.ledger_code AND l.document_type = 'inbound_confirm' AND l.document_code = m.code
 WHERE l.code IS NULL;
SELECT 'missing' AS kind, 'mother_item_ledger' AS item, COUNT(*) AS value
  FROM biz_mother_order_item i
  LEFT JOIN inv_stock_ledger l ON l.code = i.ledger_code AND l.document_type = 'inbound_confirm' AND l.document_code = i.mother_order_code
 WHERE l.code IS NULL;
SELECT 'missing' AS kind, 'receipt_line_ledger' AS item, COUNT(*) AS value
  FROM biz_receipt_line r
  LEFT JOIN inv_stock_ledger l ON l.code = r.ledger_code AND l.document_type = 'inbound_confirm' AND l.document_code = r.mother_order_code
 WHERE l.code IS NULL;
SELECT 'missing' AS kind, 'work_order_ledger' AS item, COUNT(*) AS value
  FROM biz_work_order w
  LEFT JOIN inv_stock_ledger l ON l.code = w.ledger_code AND l.document_type = 'work_order_split' AND l.document_code = w.code
 WHERE l.code IS NULL;
SELECT 'missing' AS kind, 'allocation_ledger' AS item, COUNT(*) AS value
  FROM biz_child_line_allocation a
  LEFT JOIN inv_stock_ledger l ON l.code = a.ledger_code AND l.document_type = 'work_order_split' AND l.document_code = a.child_order_code
 WHERE l.code IS NULL;
SELECT 'missing' AS kind, 'handoff_ledger' AS item, COUNT(*) AS value
  FROM biz_process_station_handoff h
  LEFT JOIN inv_stock_ledger l ON l.code = h.ledger_code
   AND l.document_type IN ('process_station_report', 'production_report')
   AND l.document_code = h.order_code
 WHERE l.code IS NULL;
SELECT 'missing' AS kind, 'outbound_ledger' AS item, COUNT(*) AS value
  FROM biz_outbound_order o
  LEFT JOIN inv_stock_ledger l ON l.code = o.ledger_code AND l.document_type = 'outbound_shipment' AND l.document_code = o.code
 WHERE l.code IS NULL;
SELECT 'missing' AS kind, 'outbound_item_ledger' AS item, COUNT(*) AS value
  FROM biz_outbound_order_item i
  LEFT JOIN inv_stock_ledger l ON l.code = i.ledger_code AND l.document_type = 'outbound_shipment' AND l.document_code = i.outbound_order_code
 WHERE l.code IS NULL;
SELECT 'missing' AS kind, 'quality_freeze_ledger' AS item, COUNT(*) AS value
  FROM biz_quality_requirement_check c
  LEFT JOIN inv_stock_ledger l ON l.code = c.ledger_code AND l.document_type = 'quality_freeze' AND l.document_code = c.quality_case_code
 WHERE c.frozen = 1 AND l.code IS NULL;
SELECT 'missing' AS kind, 'quality_round_ledger' AS item, COUNT(*) AS value
  FROM biz_quality_round r
  LEFT JOIN inv_stock_ledger l ON l.code = r.ledger_code
 WHERE r.ledger_code <> '' AND l.code IS NULL;
SELECT 'missing' AS kind, 'quality_round_frozen_without_ledger' AS item, COUNT(*) AS value
  FROM biz_quality_round WHERE frozen = 1 AND ledger_code = '';
SELECT 'missing' AS kind, 'quality_rework_report_ledger_pointer' AS item, COUNT(*) AS value
  FROM biz_quality_round r
  LEFT JOIN inv_stock_ledger l ON l.code = r.rework_report_ledger AND l.document_type = 'rework_report'
 WHERE r.rework_report_ledger <> '' AND l.code IS NULL;
SELECT 'missing' AS kind, 'rework_report_ledger' AS item, COUNT(*) AS value
  FROM biz_quality_rework_report r
  LEFT JOIN inv_stock_ledger l ON l.code = r.ledger_code AND l.document_type = 'rework_report' AND l.document_code = r.rework_order_code
 WHERE l.code IS NULL;
SELECT 'missing' AS kind, 'balance_last_ledger' AS item, COUNT(*) AS value
  FROM inv_stock_balance b
  LEFT JOIN inv_stock_ledger l ON l.code = b.last_ledger_code
 WHERE b.last_ledger_code IS NOT NULL AND b.last_ledger_code <> '' AND l.code IS NULL;

-- 已生效且写了差额的改单，必须追加应收或工资，不能只改原账单。
SELECT 'missing' AS kind, 'applied_change_receivable_increase' AS item, COUNT(*) AS value
  FROM biz_order_change c
 WHERE c.status_code = 'APPLIED' AND c.amount_unchanged = 0 AND c.customer_delta > 0
   AND NOT EXISTS (
       SELECT 1 FROM fin_receivable_line r
        WHERE r.source_type = 'CHANGE' AND r.source_key LIKE CONCAT('CHANGE|', c.id, '|%')
   );
SELECT 'missing' AS kind, 'applied_change_receivable_decrease' AS item, COUNT(*) AS value
  FROM biz_order_change c
 WHERE c.status_code = 'APPLIED' AND c.amount_unchanged = 0 AND c.customer_delta < 0
   AND NOT EXISTS (
       SELECT 1 FROM fin_receivable_line r
        WHERE r.source_type = 'RED' AND r.source_key = CONCAT('CHANGE-RED|', c.id)
   );
SELECT 'missing' AS kind, 'applied_change_payroll' AS item, COUNT(*) AS value
  FROM biz_order_change c
 WHERE c.status_code = 'APPLIED' AND c.amount_unchanged = 0 AND c.worker_delta <> 0
   AND NOT EXISTS (
       SELECT 1 FROM fin_payroll_line p WHERE p.source_key = CONCAT('PAY-ADJ|', c.id)
   );
SELECT 'missing' AS kind, 'confirmed_month_handoff_payroll' AS item, COUNT(*) AS value
  FROM biz_process_station_handoff h
  JOIN fin_payroll_run run ON run.confirmed = 1 AND run.year_month = LEFT(h.created_at, 7)
 WHERE h.order_code NOT LIKE 'FG-%'
   AND NOT EXISTS (
       SELECT 1 FROM fin_payroll_line p
        WHERE p.year_month = run.year_month
          AND p.source_key = CONCAT('PAY|', h.order_code, '|', h.process_name)
   );
SELECT 'missing' AS kind, 'consumable_purchase_payable' AS item, COUNT(*) AS value
  FROM fin_consumable_movement m
  LEFT JOIN fin_payable p ON p.code = m.payable_code
 WHERE m.direction = 'PURCHASE' AND (m.payable_code = '' OR p.code IS NULL);

-- 单据上写了证据编号，但附件元数据不在。空编号表示尚未采集，不计缺失。
-- 母单证据编号无法解析时单独计数；其中没有业务引用的是子集，合计时不要两行相加。
SELECT 'missing' AS kind, 'mother_evidence_code_unresolved' AS item, COUNT(*) AS value
  FROM biz_mother_order m
  LEFT JOIN doc_evidence_attachment a ON a.code = m.evidence_code
 WHERE m.evidence_code IS NOT NULL AND TRIM(m.evidence_code) <> '' AND a.code IS NULL;
SELECT 'missing' AS kind, 'mother_unresolved_evidence_without_link' AS item, COUNT(*) AS value
  FROM biz_mother_order m
  LEFT JOIN doc_evidence_attachment a ON a.code = m.evidence_code
 WHERE m.evidence_code IS NOT NULL AND TRIM(m.evidence_code) <> '' AND a.code IS NULL
   AND NOT EXISTS (
       SELECT 1 FROM doc_business_evidence_link l
        WHERE l.target_type = 'MOTHER_ORDER' AND l.target_code = m.code
   );
SELECT 'missing' AS kind, 'outbound_evidence' AS item, COUNT(*) AS value
  FROM biz_outbound_order o
  LEFT JOIN doc_evidence_attachment a ON a.code = o.evidence_code
 WHERE o.evidence_code IS NOT NULL AND TRIM(o.evidence_code) <> '' AND a.code IS NULL;
-- 账号前置审批把申请单号写在 evidence_code，不是附件编号。损耗审批的非空编号才应对附件。
SELECT 'missing' AS kind, 'loss_approval_evidence' AS item, COUNT(*) AS value
  FROM approval_task t
  LEFT JOIN doc_evidence_attachment a ON a.code = t.evidence_code
 WHERE t.scene = 'LOSS_APPROVAL'
   AND t.evidence_code IS NOT NULL AND TRIM(t.evidence_code) <> '' AND a.code IS NULL;
SELECT 'missing' AS kind, 'other_approval_evidence' AS item, COUNT(*) AS value
  FROM approval_task t
  LEFT JOIN doc_evidence_attachment a ON a.code = t.evidence_code
 WHERE t.scene NOT IN ('ACCOUNT_PRE_APPROVAL', 'LOSS_APPROVAL')
   AND t.evidence_code IS NOT NULL AND TRIM(t.evidence_code) <> '' AND a.code IS NULL;
SELECT 'missing' AS kind, 'other_approval_audit_evidence' AS item, COUNT(*) AS value
  FROM approval_audit_event e
  JOIN approval_task t ON t.approval_code = e.approval_code
  LEFT JOIN doc_evidence_attachment a ON a.code = e.evidence_code
 WHERE t.scene NOT IN ('ACCOUNT_PRE_APPROVAL')
   AND e.evidence_code IS NOT NULL AND TRIM(e.evidence_code) <> '' AND a.code IS NULL;
SELECT 'missing' AS kind, 'evidence_link_attachment' AS item, COUNT(*) AS value
  FROM doc_business_evidence_link l
  LEFT JOIN doc_evidence_attachment a ON a.code = l.attachment_code
 WHERE a.code IS NULL;

-- 台账在，但声明的业务单据不在。退货、售后、备料和公司料没有单独单据表，台账本身就是事实。
SELECT 'orphan' AS kind, 'ledger_inbound_without_mother' AS item, COUNT(*) AS value
  FROM inv_stock_ledger l
  LEFT JOIN biz_mother_order m ON m.code = l.document_code
 WHERE l.document_type = 'inbound_confirm' AND m.code IS NULL;
SELECT 'orphan' AS kind, 'ledger_split_without_order' AS item, COUNT(*) AS value
  FROM inv_stock_ledger l
  LEFT JOIN biz_work_order w ON w.code = l.document_code
  LEFT JOIN biz_mother_order m ON m.code = l.document_code
 WHERE l.document_type = 'work_order_split' AND w.code IS NULL AND m.code IS NULL;
SELECT 'orphan' AS kind, 'ledger_station_without_handoff' AS item, COUNT(*) AS value
  FROM inv_stock_ledger l
  LEFT JOIN biz_process_station_handoff h ON h.ledger_code = l.code AND h.order_code = l.document_code
 WHERE l.document_type = 'process_station_report' AND h.id IS NULL;
SELECT 'orphan' AS kind, 'ledger_production_without_order' AS item, COUNT(*) AS value
  FROM inv_stock_ledger l
  LEFT JOIN biz_work_order w ON w.code = l.document_code
  LEFT JOIN biz_mother_order m ON m.code = l.document_code
  LEFT JOIN biz_execution_round r ON r.round_work_order_code = l.document_code
 WHERE l.document_type = 'production_report' AND w.code IS NULL AND m.code IS NULL AND r.id IS NULL;
SELECT 'orphan' AS kind, 'ledger_outbound_without_order' AS item, COUNT(*) AS value
  FROM inv_stock_ledger l
  LEFT JOIN biz_outbound_order o ON o.code = l.document_code
 WHERE l.document_type = 'outbound_shipment' AND o.code IS NULL;
SELECT 'orphan' AS kind, 'ledger_quality_freeze_without_case' AS item, COUNT(*) AS value
  FROM inv_stock_ledger l
  LEFT JOIN biz_quality_requirement_check c ON c.quality_case_code = l.document_code
  LEFT JOIN biz_quality_round r ON r.quality_case_code = l.document_code
 WHERE l.document_type = 'quality_freeze' AND c.id IS NULL AND r.id IS NULL;
SELECT 'orphan' AS kind, 'ledger_quality_release_without_case' AS item, COUNT(*) AS value
  FROM inv_stock_ledger l
  LEFT JOIN biz_quality_requirement_check c ON c.quality_case_code = l.document_code
  LEFT JOIN biz_quality_round r ON r.quality_case_code = l.document_code
 WHERE l.document_type = 'quality_release' AND c.id IS NULL AND r.id IS NULL;
SELECT 'orphan' AS kind, 'ledger_rework_dispatch_without_case' AS item, COUNT(*) AS value
  FROM inv_stock_ledger l
  LEFT JOIN biz_quality_requirement_check c ON c.rework_order_code = l.document_code
  LEFT JOIN biz_quality_round r ON r.rework_order_code = l.document_code
 WHERE l.document_type = 'rework_dispatch' AND c.id IS NULL AND r.id IS NULL;
SELECT 'orphan' AS kind, 'ledger_rework_report_without_report' AS item, COUNT(*) AS value
  FROM inv_stock_ledger l
  LEFT JOIN biz_quality_rework_report r ON r.rework_order_code = l.document_code
 WHERE l.document_type = 'rework_report' AND r.id IS NULL;
SELECT 'orphan' AS kind, 'ledger_return_without_mother' AS item, COUNT(*) AS value
  FROM inv_stock_ledger l
  LEFT JOIN biz_mother_order m ON m.code = l.mother_order_code
 WHERE l.document_type IN ('customer_return', 'after_sales_review')
   AND (l.mother_order_code IS NULL OR l.mother_order_code = '' OR m.code IS NULL);
SELECT 'orphan' AS kind, 'ledger_prep_issue_without_mother' AS item, COUNT(*) AS value
  FROM inv_stock_ledger l
  LEFT JOIN biz_mother_order m ON m.code = l.document_code
 WHERE l.document_type IN ('prep_issue', 'prep_return') AND m.code IS NULL;
SELECT 'orphan' AS kind, 'ledger_unrecognized' AS item, COUNT(*) AS value
  FROM inv_stock_ledger
 WHERE document_type NOT IN (
       'inbound_confirm', 'work_order_split', 'process_station_report', 'production_report',
       'quality_freeze', 'quality_release', 'rework_dispatch', 'rework_report',
       'outbound_shipment', 'customer_return', 'after_sales_review',
       'prep_receipt', 'prep_issue', 'prep_return',
       'company_receipt', 'company_issue', 'company_return',
       'inbound_reversal'
   );

-- 账单在，但来源单据或原账单不在。红字和改单增额必须还能找到原业务，不把原应收删掉。
SELECT 'orphan' AS kind, 'receivable_inbound_without_mother' AS item, COUNT(*) AS value
  FROM fin_receivable_line r
  LEFT JOIN biz_mother_order m ON m.code = SUBSTRING(r.source_key, 9)
 WHERE r.source_type = 'INBOUND' AND (r.source_key NOT LIKE 'INBOUND|%' OR m.code IS NULL);
SELECT 'orphan' AS kind, 'receivable_process_without_handoff' AS item, COUNT(*) AS value
  FROM fin_receivable_line r
  LEFT JOIN biz_process_station_handoff h
    ON h.order_code = SUBSTRING_INDEX(SUBSTRING(r.source_key, 9), '|', 1)
   AND h.process_name = SUBSTRING_INDEX(r.source_key, '|', -1)
 WHERE r.source_type = 'PROCESS'
   AND (r.source_key NOT LIKE 'PROCESS|%|%' OR h.id IS NULL);
SELECT 'orphan' AS kind, 'receivable_change_without_applied_change' AS item, COUNT(*) AS value
  FROM fin_receivable_line r
  LEFT JOIN biz_order_change c
    ON c.id = SUBSTRING_INDEX(SUBSTRING(r.source_key, 8), '|', 1) AND c.status_code = 'APPLIED'
 WHERE r.source_type = 'CHANGE'
   AND (r.source_key NOT LIKE 'CHANGE|%' OR c.id IS NULL);
SELECT 'orphan' AS kind, 'receivable_red_without_change' AS item, COUNT(*) AS value
  FROM fin_receivable_line r
  LEFT JOIN biz_order_change c ON c.id = SUBSTRING(r.source_key, 12) AND c.status_code = 'APPLIED'
 WHERE r.source_type = 'RED' AND r.source_key LIKE 'CHANGE-RED|%' AND c.id IS NULL;
SELECT 'orphan' AS kind, 'receivable_red_without_original' AS item, COUNT(*) AS value
  FROM fin_receivable_line r
  LEFT JOIN fin_receivable_line original ON original.code = r.reverses_code AND original.source_type <> 'RED'
 WHERE r.source_type = 'RED' AND (r.reverses_code = '' OR original.code IS NULL);
SELECT 'orphan' AS kind, 'receivable_unrecognized_source' AS item, COUNT(*) AS value
  FROM fin_receivable_line
 WHERE source_type NOT IN ('INBOUND', 'PROCESS', 'CHANGE', 'RED');
SELECT 'orphan' AS kind, 'receipt_allocation_without_receipt' AS item, COUNT(*) AS value
  FROM fin_receipt_allocation a
  LEFT JOIN fin_customer_receipt r ON r.code = a.receipt_code
 WHERE r.code IS NULL;
SELECT 'orphan' AS kind, 'receipt_allocation_without_receivable' AS item, COUNT(*) AS value
  FROM fin_receipt_allocation a
  LEFT JOIN fin_receivable_line r ON r.code = a.receivable_code
 WHERE r.code IS NULL;
SELECT 'orphan' AS kind, 'payroll_without_handoff' AS item, COUNT(*) AS value
  FROM fin_payroll_line p
  LEFT JOIN biz_process_station_handoff h
    ON h.order_code = SUBSTRING_INDEX(SUBSTRING(p.source_key, 5), '|', 1)
   AND h.process_name = SUBSTRING_INDEX(p.source_key, '|', -1)
 WHERE p.source_key LIKE 'PAY|%' AND h.id IS NULL;
SELECT 'orphan' AS kind, 'payroll_adjustment_without_change' AS item, COUNT(*) AS value
  FROM fin_payroll_line p
  LEFT JOIN biz_order_change c ON c.id = SUBSTRING(p.source_key, 9) AND c.status_code = 'APPLIED'
 WHERE p.source_key LIKE 'PAY-ADJ|%' AND c.id IS NULL;
SELECT 'orphan' AS kind, 'payroll_unrecognized_source' AS item, COUNT(*) AS value
  FROM fin_payroll_line
 WHERE source_key NOT LIKE 'PAY|%' AND source_key NOT LIKE 'PAY-ADJ|%';
SELECT 'orphan' AS kind, 'payable_payroll_without_run' AS item, COUNT(*) AS value
  FROM fin_payable p
  LEFT JOIN fin_payroll_run run
    ON run.confirmed = 1
   AND run.year_month = SUBSTRING_INDEX(SUBSTRING_INDEX(p.source_code, '|', 2), '|', -1)
 WHERE p.kind = 'PAYROLL' AND p.source_code LIKE 'PAYROLL|%' AND p.source_code NOT LIKE 'PAYROLL-ADJ|%'
   AND run.year_month IS NULL;
SELECT 'orphan' AS kind, 'payable_payroll_adjustment_without_change' AS item, COUNT(*) AS value
  FROM fin_payable p
  LEFT JOIN biz_order_change c ON c.id = SUBSTRING(p.source_code, 13) AND c.status_code = 'APPLIED'
 WHERE p.source_code LIKE 'PAYROLL-ADJ|%' AND c.id IS NULL;
SELECT 'orphan' AS kind, 'payable_consumable_without_movement' AS item, COUNT(*) AS value
  FROM fin_payable p
  LEFT JOIN fin_consumable_movement m ON m.payable_code = p.code AND m.direction = 'PURCHASE'
 WHERE p.kind = 'CONSUMABLE' AND m.code IS NULL;
SELECT 'orphan' AS kind, 'payable_unrecognized_kind' AS item, COUNT(*) AS value
  FROM fin_payable WHERE kind NOT IN ('PAYROLL', 'CONSUMABLE', 'OUTSOURCE');
SELECT 'orphan' AS kind, 'payable_payment_without_payable' AS item, COUNT(*) AS value
  FROM fin_payable_payment pay
  LEFT JOIN fin_payable p ON p.code = pay.payable_code
 WHERE p.code IS NULL;
SELECT 'orphan' AS kind, 'consumable_movement_without_item' AS item, COUNT(*) AS value
  FROM fin_consumable_movement m
  LEFT JOIN fin_consumable_item i ON i.code = m.item_code
 WHERE i.code IS NULL;
SELECT 'orphan' AS kind, 'depreciation_without_asset' AS item, COUNT(*) AS value
  FROM fin_depreciation_entry d
  LEFT JOIN fin_fixed_asset a ON a.code = d.asset_code
 WHERE a.code IS NULL;

-- 证据声明了单据或操作，但该业务来源不在。未填写单据类型的附件保持待关联，不在这里计数。
SELECT 'orphan' AS kind, 'evidence_link_target' AS item, COUNT(*) AS value
  FROM doc_business_evidence_link l
  LEFT JOIN biz_mother_order m ON l.target_type = 'MOTHER_ORDER' AND m.code = l.target_code
  LEFT JOIN biz_work_order w ON l.target_type = 'WORK_ORDER' AND w.code = l.target_code
  LEFT JOIN biz_outbound_order o ON l.target_type = 'OUTBOUND_ORDER' AND o.code = l.target_code
  LEFT JOIN biz_quality_requirement_check c ON l.target_type = 'REWORK' AND c.rework_order_code = l.target_code
  LEFT JOIN biz_quality_round qr ON l.target_type = 'REWORK' AND qr.rework_order_code = l.target_code
  LEFT JOIN biz_quality_rework_report rr ON l.target_type = 'REWORK' AND rr.rework_order_code = l.target_code
 WHERE l.target_type NOT IN ('MOTHER_ORDER', 'WORK_ORDER', 'OUTBOUND_ORDER', 'REWORK')
    OR (l.target_type = 'MOTHER_ORDER' AND m.code IS NULL)
    OR (l.target_type = 'WORK_ORDER' AND w.code IS NULL)
    OR (l.target_type = 'OUTBOUND_ORDER' AND o.code IS NULL)
    OR (l.target_type = 'REWORK' AND c.id IS NULL AND qr.id IS NULL AND rr.id IS NULL);
SELECT 'orphan' AS kind, 'evidence_link_operation' AS item, COUNT(*) AS value
  FROM doc_business_evidence_link l
  LEFT JOIN biz_mother_order intake ON l.operation_type = 'inbound_confirm' AND intake.ledger_code = l.operation_code
  LEFT JOIN biz_process_station_handoff handoff
    ON l.operation_type IN ('process_station_report', 'production_report') AND handoff.ledger_code = l.operation_code
  LEFT JOIN biz_quality_requirement_check qcheck
    ON l.operation_type = 'quality_check' AND qcheck.quality_case_code = l.operation_code
  LEFT JOIN inv_stock_ledger freeze_ledger
    ON l.operation_type = 'quality_freeze' AND freeze_ledger.code = l.operation_code AND freeze_ledger.document_type = 'quality_freeze'
  LEFT JOIN inv_stock_ledger release_ledger
    ON l.operation_type = 'quality_release' AND release_ledger.code = l.operation_code AND release_ledger.document_type = 'quality_release'
  LEFT JOIN biz_quality_requirement_check rework
    ON l.operation_type = 'rework_dispatch' AND rework.rework_order_code = l.operation_code
  LEFT JOIN inv_stock_ledger rework_ledger
    ON l.operation_type = 'rework_report' AND rework_ledger.code = l.operation_code AND rework_ledger.document_type = 'rework_report'
  LEFT JOIN biz_outbound_order shipment
    ON l.operation_type = 'outbound_shipment' AND shipment.code = l.operation_code
  LEFT JOIN biz_order_change change_row
    ON l.operation_type = 'ORDER_CHANGE' AND change_row.id = l.operation_code
  LEFT JOIN scan_operation_progress note
    ON l.operation_type = 'progress_note' AND note.progress_code = l.operation_code
 WHERE l.operation_type NOT IN (
           'inbound_confirm', 'process_station_report', 'production_report', 'quality_check',
           'quality_freeze', 'quality_release', 'rework_dispatch', 'rework_report',
           'outbound_shipment', 'ORDER_CHANGE', 'progress_note'
       )
    OR (l.operation_type = 'inbound_confirm' AND intake.code IS NULL)
    OR (l.operation_type IN ('process_station_report', 'production_report') AND handoff.id IS NULL)
    OR (l.operation_type = 'quality_check' AND qcheck.id IS NULL)
    OR (l.operation_type = 'quality_freeze' AND freeze_ledger.code IS NULL)
    OR (l.operation_type = 'quality_release' AND release_ledger.code IS NULL)
    OR (l.operation_type = 'rework_dispatch' AND rework.id IS NULL)
    OR (l.operation_type = 'rework_report' AND rework_ledger.code IS NULL)
    OR (l.operation_type = 'outbound_shipment' AND shipment.code IS NULL)
    OR (l.operation_type = 'ORDER_CHANGE' AND change_row.id IS NULL)
    OR (l.operation_type = 'progress_note' AND note.id IS NULL);
SELECT 'orphan' AS kind, 'pre_approval_without_application' AS item, COUNT(*) AS value
  FROM approval_task t
  LEFT JOIN auth_account_pre_approval_application a ON a.application_code = t.evidence_code
 WHERE t.scene = 'ACCOUNT_PRE_APPROVAL'
   AND (t.evidence_code IS NULL OR TRIM(t.evidence_code) = '' OR a.application_code IS NULL);
SELECT 'orphan' AS kind, 'pre_approval_audit_without_application' AS item, COUNT(*) AS value
  FROM approval_audit_event e
  JOIN approval_task t ON t.approval_code = e.approval_code
  LEFT JOIN auth_account_pre_approval_application a ON a.application_code = e.evidence_code
 WHERE t.scene = 'ACCOUNT_PRE_APPROVAL'
   AND e.evidence_code IS NOT NULL AND TRIM(e.evidence_code) <> ''
   AND a.application_code IS NULL;
SELECT 'orphan' AS kind, 'evidence_attachment_without_document' AS item, COUNT(*) AS value
  FROM doc_evidence_attachment a
  LEFT JOIN biz_mother_order m ON a.object_type = 'MOTHER_ORDER' AND m.code = a.business_code
  LEFT JOIN biz_work_order w ON a.object_type = 'WORK_ORDER' AND w.code = a.business_code
  LEFT JOIN biz_outbound_order o ON a.object_type = 'OUTBOUND_ORDER' AND o.code = a.business_code
  LEFT JOIN biz_quality_requirement_check c ON a.object_type = 'REWORK' AND c.rework_order_code = a.business_code
  LEFT JOIN biz_quality_round qr ON a.object_type = 'REWORK' AND qr.rework_order_code = a.business_code
  LEFT JOIN biz_quality_rework_report rr ON a.object_type = 'REWORK' AND rr.rework_order_code = a.business_code
 WHERE a.object_type IN ('MOTHER_ORDER', 'WORK_ORDER', 'OUTBOUND_ORDER', 'REWORK')
   AND (a.business_code IS NULL OR TRIM(a.business_code) = ''
        OR (a.object_type = 'MOTHER_ORDER' AND m.code IS NULL)
        OR (a.object_type = 'WORK_ORDER' AND w.code IS NULL)
        OR (a.object_type = 'OUTBOUND_ORDER' AND o.code IS NULL)
        OR (a.object_type = 'REWORK' AND c.id IS NULL AND qr.id IS NULL AND rr.id IS NULL));

COMMIT;
