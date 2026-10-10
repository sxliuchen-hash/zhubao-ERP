-- 经营核算。客户加工费、收款、工资、消耗品、固定资产和外协应付。不做会计凭证。

CREATE TABLE IF NOT EXISTS fin_price_agreement (
    code VARCHAR(60) PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    customer_name VARCHAR(120) NOT NULL,
    pricing_mode VARCHAR(20) NOT NULL,
    effective_from CHAR(7) NOT NULL,
    effective_to CHAR(7) NULL,
    operator_name VARCHAR(80) NOT NULL,
    created_at VARCHAR(40) NOT NULL,
    KEY idx_fin_agreement_customer (customer_id)
);

CREATE TABLE IF NOT EXISTS fin_price_line (
    agreement_code VARCHAR(60) NOT NULL,
    line_no INT NOT NULL,
    process_name VARCHAR(40) NOT NULL,
    charge_unit VARCHAR(20) NOT NULL,
    unit_price DECIMAL(18, 4) NOT NULL,
    PRIMARY KEY (agreement_code, line_no)
);

CREATE TABLE IF NOT EXISTS fin_receivable_line (
    code VARCHAR(80) PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    customer_name VARCHAR(120) NOT NULL,
    year_month CHAR(7) NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    source_key VARCHAR(160) NOT NULL,
    mother_order_code VARCHAR(80) NOT NULL,
    work_order_code VARCHAR(80) NOT NULL,
    process_name VARCHAR(40) NOT NULL,
    quantity DECIMAL(18, 3) NOT NULL,
    charge_unit VARCHAR(20) NOT NULL,
    unit_price DECIMAL(18, 4) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    reverses_code VARCHAR(80) NOT NULL,
    remark VARCHAR(200) NOT NULL,
    operator_name VARCHAR(80) NOT NULL,
    created_at VARCHAR(40) NOT NULL,
    UNIQUE KEY uk_fin_receivable_source (source_key),
    KEY idx_fin_receivable_customer (customer_id, year_month)
);

CREATE TABLE IF NOT EXISTS fin_customer_receipt (
    code VARCHAR(80) PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    customer_name VARCHAR(120) NOT NULL,
    year_month CHAR(7) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    received_on CHAR(10) NOT NULL,
    operator_name VARCHAR(80) NOT NULL,
    created_at VARCHAR(40) NOT NULL,
    KEY idx_fin_receipt_customer (customer_id, year_month)
);

CREATE TABLE IF NOT EXISTS fin_receipt_allocation (
    receipt_code VARCHAR(80) NOT NULL,
    line_no INT NOT NULL,
    receivable_code VARCHAR(80) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    PRIMARY KEY (receipt_code, line_no)
);

CREATE TABLE IF NOT EXISTS fin_worker_rate (
    code VARCHAR(80) PRIMARY KEY,
    process_name VARCHAR(40) NOT NULL,
    charge_unit VARCHAR(20) NOT NULL,
    unit_price DECIMAL(18, 4) NOT NULL,
    effective_from CHAR(7) NOT NULL,
    effective_to CHAR(7) NULL,
    operator_name VARCHAR(80) NOT NULL,
    created_at VARCHAR(40) NOT NULL,
    KEY idx_fin_worker_rate_process (process_name)
);

CREATE TABLE IF NOT EXISTS fin_payroll_line (
    code VARCHAR(80) PRIMARY KEY,
    year_month CHAR(7) NOT NULL,
    operator_name VARCHAR(80) NOT NULL,
    order_code VARCHAR(80) NOT NULL,
    process_name VARCHAR(40) NOT NULL,
    mother_order_code VARCHAR(80) NOT NULL,
    customer_id BIGINT NOT NULL,
    quantity DECIMAL(18, 3) NOT NULL,
    charge_unit VARCHAR(20) NOT NULL,
    unit_price DECIMAL(18, 4) NOT NULL,
    gross_amount DECIMAL(18, 2) NOT NULL,
    loss_deduction DECIMAL(18, 2) NOT NULL,
    net_amount DECIMAL(18, 2) NOT NULL,
    confirmed TINYINT(1) NOT NULL,
    source_key VARCHAR(160) NOT NULL,
    KEY idx_fin_payroll_month (year_month)
);

CREATE TABLE IF NOT EXISTS fin_payroll_run (
    year_month CHAR(7) PRIMARY KEY,
    confirmed TINYINT(1) NOT NULL,
    confirmed_by VARCHAR(80) NOT NULL,
    confirmed_at VARCHAR(40) NOT NULL
);

CREATE TABLE IF NOT EXISTS fin_consumable_item (
    code VARCHAR(80) PRIMARY KEY,
    item_name VARCHAR(120) NOT NULL,
    unit_name VARCHAR(40) NOT NULL
);

CREATE TABLE IF NOT EXISTS fin_consumable_movement (
    code VARCHAR(80) PRIMARY KEY,
    item_code VARCHAR(80) NOT NULL,
    direction VARCHAR(20) NOT NULL,
    quantity DECIMAL(18, 3) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    supplier_name VARCHAR(120) NOT NULL,
    customer_id BIGINT NULL,
    year_month CHAR(7) NOT NULL,
    payable_code VARCHAR(80) NOT NULL,
    operator_name VARCHAR(80) NOT NULL,
    created_at VARCHAR(40) NOT NULL,
    KEY idx_fin_consumable_item (item_code, year_month)
);

CREATE TABLE IF NOT EXISTS fin_fixed_asset (
    code VARCHAR(80) PRIMARY KEY,
    asset_name VARCHAR(120) NOT NULL,
    category_name VARCHAR(80) NOT NULL,
    original_value DECIMAL(18, 2) NOT NULL,
    residual_value DECIMAL(18, 2) NOT NULL,
    start_month CHAR(7) NOT NULL,
    useful_months INT NOT NULL,
    operator_name VARCHAR(80) NOT NULL,
    created_at VARCHAR(40) NOT NULL
);

CREATE TABLE IF NOT EXISTS fin_depreciation_entry (
    code VARCHAR(80) PRIMARY KEY,
    asset_code VARCHAR(80) NOT NULL,
    year_month CHAR(7) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    UNIQUE KEY uk_fin_depreciation (asset_code, year_month)
);

CREATE TABLE IF NOT EXISTS fin_payable (
    code VARCHAR(80) PRIMARY KEY,
    kind VARCHAR(20) NOT NULL,
    year_month CHAR(7) NOT NULL,
    payee VARCHAR(120) NOT NULL,
    source_code VARCHAR(160) NOT NULL,
    customer_id BIGINT NULL,
    mother_order_code VARCHAR(80) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    paid_amount DECIMAL(18, 2) NOT NULL,
    operator_name VARCHAR(80) NOT NULL,
    created_at VARCHAR(40) NOT NULL,
    KEY idx_fin_payable_source (source_code),
    KEY idx_fin_payable_month (year_month)
);

CREATE TABLE IF NOT EXISTS fin_payable_payment (
    code VARCHAR(80) PRIMARY KEY,
    payable_code VARCHAR(80) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    paid_on CHAR(10) NOT NULL,
    operator_name VARCHAR(80) NOT NULL,
    created_at VARCHAR(40) NOT NULL,
    KEY idx_fin_payment_payable (payable_code)
);

INSERT INTO sys_navigation_menu (code, label, module, route, button_codes, sort_order)
SELECT 'finance', '财务', '客户账 / 工资 / 成本', '/finance',
       'FINANCE_PRICE_MANAGE,FINANCE_BILLING_POST,FINANCE_RECEIPT,FINANCE_PAYROLL,FINANCE_CONSUMABLE,FINANCE_ASSET,FINANCE_PAYABLE',
       65
WHERE NOT EXISTS (SELECT 1 FROM sys_navigation_menu WHERE code = 'finance');

INSERT INTO sys_role_menu (role_code, menu_code)
SELECT 'ADMIN', 'finance'
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_code = 'ADMIN' AND menu_code = 'finance');

INSERT INTO sys_role_menu (role_code, menu_code)
SELECT 'GENERAL_MANAGER', 'finance'
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_code = 'GENERAL_MANAGER' AND menu_code = 'finance');

INSERT INTO sys_role_menu (role_code, menu_code)
SELECT 'FINANCE_STATISTICIAN', 'finance'
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_code = 'FINANCE_STATISTICIAN' AND menu_code = 'finance');
