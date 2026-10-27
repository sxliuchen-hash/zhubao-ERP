-- 交货后结单凭据、后续核销和工资/应付来源字段。不改历史账，不删除旧表。
CREATE TABLE fin_order_settlement (
    code VARCHAR(80) PRIMARY KEY,
    root_code VARCHAR(80) NOT NULL,
    version_no INT NOT NULL,
    previous_code VARCHAR(80) NOT NULL DEFAULT '',
    target_type VARCHAR(20) NOT NULL,
    target_code VARCHAR(80) NOT NULL,
    mother_order_code VARCHAR(80) NOT NULL,
    customer_id BIGINT NOT NULL,
    customer_name VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL,
    row_version INT NOT NULL,
    delivery_revision VARCHAR(80) NOT NULL,
    customer_net DECIMAL(18, 2) NOT NULL,
    employee_net DECIMAL(18, 2) NOT NULL,
    customer_waived TINYINT(1) NOT NULL,
    waive_reason VARCHAR(400) NOT NULL,
    no_internal_labor TINYINT(1) NOT NULL,
    no_labor_reason VARCHAR(400) NOT NULL,
    preparer VARCHAR(80) NOT NULL,
    confirmed_by VARCHAR(80) NOT NULL,
    created_at VARCHAR(40) NOT NULL,
    confirmed_at VARCHAR(40) NOT NULL,
    posting_month CHAR(7) NOT NULL,
    legacy_state VARCHAR(20) NOT NULL,
    draft_key VARCHAR(160) NULL,
    opening_key VARCHAR(20) NULL,
    UNIQUE KEY uk_fin_settlement_target_ver (target_type, target_code, version_no),
    UNIQUE KEY uk_fin_settlement_draft (draft_key),
    UNIQUE KEY uk_fin_settlement_opening (target_type, target_code, opening_key),
    KEY idx_fin_settlement_mother (mother_order_code, status),
    KEY idx_fin_settlement_customer (customer_id, posting_month)
);

CREATE TABLE fin_order_settlement_item (
    settlement_code VARCHAR(80) NOT NULL,
    line_no INT NOT NULL,
    kind VARCHAR(20) NOT NULL,
    title VARCHAR(120) NOT NULL,
    source_ref VARCHAR(160) NOT NULL,
    charge_mode VARCHAR(20) NOT NULL,
    quantity DECIMAL(18, 3) NULL,
    quantity_unit VARCHAR(20) NOT NULL,
    charge_unit VARCHAR(20) NOT NULL,
    unit_price DECIMAL(18, 4) NULL,
    gross_amount DECIMAL(18, 2) NULL,
    deduction DECIMAL(18, 2) NOT NULL,
    net_amount DECIMAL(18, 2) NULL,
    payee_account VARCHAR(80) NOT NULL,
    payee_name VARCHAR(80) NOT NULL,
    remark VARCHAR(400) NOT NULL,
    loss_source_code VARCHAR(80) NOT NULL,
    PRIMARY KEY (settlement_code, line_no)
);

CREATE TABLE fin_order_settlement_source (
    settlement_code VARCHAR(80) NOT NULL,
    line_no INT NOT NULL,
    source_kind VARCHAR(40) NOT NULL,
    source_code VARCHAR(80) NOT NULL,
    snapshot_text VARCHAR(500) NOT NULL,
    PRIMARY KEY (settlement_code, line_no)
);

CREATE TABLE fin_order_settlement_posting (
    settlement_code VARCHAR(80) NOT NULL,
    item_line_no INT NOT NULL,
    ledger_kind VARCHAR(20) NOT NULL,
    ledger_code VARCHAR(80) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    PRIMARY KEY (settlement_code, item_line_no, ledger_kind),
    KEY idx_fin_settlement_posting_ledger (ledger_kind, ledger_code)
);

CREATE TABLE fin_receipt_allocation_operation (
    code VARCHAR(80) PRIMARY KEY,
    receipt_code VARCHAR(80) NOT NULL,
    customer_id BIGINT NOT NULL,
    action_code VARCHAR(20) NOT NULL,
    reason VARCHAR(400) NOT NULL,
    operator_name VARCHAR(80) NOT NULL,
    request_id VARCHAR(80) NOT NULL,
    created_at VARCHAR(40) NOT NULL,
    UNIQUE KEY uk_fin_alloc_op_request (operator_name, request_id),
    KEY idx_fin_alloc_op_receipt (receipt_code)
);

CREATE TABLE fin_receipt_allocation_operation_line (
    operation_code VARCHAR(80) NOT NULL,
    line_no INT NOT NULL,
    receivable_code VARCHAR(80) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    reverses_ref VARCHAR(160) NOT NULL,
    PRIMARY KEY (operation_code, line_no),
    KEY idx_fin_alloc_op_line_receivable (receivable_code)
);

CREATE TABLE fin_settlement_legacy_link (
    code VARCHAR(80) PRIMARY KEY,
    settlement_code VARCHAR(80) NOT NULL,
    ledger_kind VARCHAR(20) NOT NULL,
    ledger_code VARCHAR(80) NOT NULL,
    covered_amount DECIMAL(18, 2) NOT NULL,
    UNIQUE KEY uk_fin_legacy_cover (ledger_kind, ledger_code)
);

CREATE TABLE fin_settlement_policy (
    policy_code VARCHAR(40) PRIMARY KEY,
    enabled CHAR(1) NOT NULL,
    enabled_at VARCHAR(40) NOT NULL,
    operator_name VARCHAR(80) NOT NULL
);

INSERT INTO fin_settlement_policy (policy_code, enabled, enabled_at, operator_name)
SELECT 'POST_DELIVERY', 'N', '', ''
WHERE NOT EXISTS (SELECT 1 FROM fin_settlement_policy WHERE policy_code = 'POST_DELIVERY');

CREATE INDEX idx_fin_receipt_alloc_receivable ON fin_receipt_allocation (receivable_code);

ALTER TABLE fin_payable ADD COLUMN payee_account VARCHAR(80) NOT NULL DEFAULT '';
ALTER TABLE fin_payable ADD COLUMN adjusts_code VARCHAR(80) NOT NULL DEFAULT '';
ALTER TABLE fin_payable ADD COLUMN posting_key VARCHAR(160) NULL;

CREATE UNIQUE INDEX uk_fin_payable_posting ON fin_payable (posting_key);

ALTER TABLE fin_payroll_line ADD COLUMN source_mode VARCHAR(20) NOT NULL DEFAULT 'LEGACY';
ALTER TABLE fin_payroll_line ADD COLUMN payee_account VARCHAR(80) NOT NULL DEFAULT '';

CREATE INDEX idx_fin_payroll_source_mode ON fin_payroll_line (source_mode, `year_month`);
