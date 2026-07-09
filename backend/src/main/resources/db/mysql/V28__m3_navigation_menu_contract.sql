-- M3.129 Navigation menu-tree persistence contract.
-- Moves the previously hard-coded menu tree and per-role visibility into tables so menus can be
-- customized per deployment. Seeds the original review defaults.

CREATE TABLE IF NOT EXISTS sys_navigation_menu (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(60) NOT NULL,
    label VARCHAR(120) NOT NULL,
    module VARCHAR(120),
    route VARCHAR(120),
    button_codes VARCHAR(500),
    sort_order INT NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_navigation_menu_code (code)
);

CREATE TABLE IF NOT EXISTS sys_role_menu (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_code VARCHAR(40) NOT NULL,
    menu_code VARCHAR(60) NOT NULL,
    UNIQUE KEY uk_role_menu (role_code, menu_code),
    KEY idx_role_menu_role (role_code)
);

INSERT INTO sys_navigation_menu (code, label, module, route, button_codes, sort_order) VALUES
    ('dashboard', '首页工作台', '项目进展 / 调度', '/dashboard', '', 10),
    ('inbound', '来料母单', '收货 / 拆包', '/inbound', 'BUSINESS_INBOUND_CONFIRM,DOCUMENT_ATTACHMENT_UPLOAD', 20),
    ('production', '生产工单', '排产 / 报工', '/production', 'BUSINESS_PRODUCTION_REPORT,MACHINE_OUTPUT_SUBMIT', 30),
    ('quality', '质检返工', '缺陷 / 损耗', '/quality', 'BUSINESS_QUALITY_FREEZE,BUSINESS_REWORK_DISPATCH,BUSINESS_QUALITY_RELEASE', 40),
    ('inventory', '库存追溯', '客户所有权', '/inventory', 'BUSINESS_OUTBOUND_SHIPMENT,BUSINESS_CUSTOMER_RETURN', 50),
    ('approvals', '审批工作台', '损耗 / 账号 / 异常', '/approvals', 'APPROVAL_DECISION,BUSINESS_LOSS_APPROVAL', 60),
    ('configuration', '配置基线', '参数 / 构型', '/configuration', 'AUTH_ACCOUNT_MANAGE', 70),
    ('documents', '文档附件', '证据 / 归档', '/documents', 'DOCUMENT_ATTACHMENT_UPLOAD', 80),
    ('sample', '样例库', '验收脚本', '/sample', 'REGISTER_IMPORT_REVIEW,REGISTER_IMPORT_POSTING', 90),
    ('hardware', '软硬件联动', '设备 / 兜底', '/hardware', 'VIDEO_TASK_OPERATE,GATEWAY_READING_CONSUME,GATEWAY_DEVICE_MANAGE', 100),
    ('operations', '运维中心', '日志 / 指标', '/operations', '', 110),
    ('mobile', 'PWA 作业', '扫码 / 现场', '/mobile', 'BUSINESS_PRODUCTION_REPORT,MACHINE_OUTPUT_SUBMIT', 120);

INSERT INTO sys_role_menu (role_code, menu_code) VALUES
    ('ADMIN', 'dashboard'), ('ADMIN', 'inbound'), ('ADMIN', 'production'), ('ADMIN', 'quality'),
    ('ADMIN', 'inventory'), ('ADMIN', 'approvals'), ('ADMIN', 'configuration'), ('ADMIN', 'documents'),
    ('ADMIN', 'sample'), ('ADMIN', 'hardware'), ('ADMIN', 'operations'), ('ADMIN', 'mobile'),
    ('GENERAL_MANAGER', 'dashboard'), ('GENERAL_MANAGER', 'inbound'), ('GENERAL_MANAGER', 'production'),
    ('GENERAL_MANAGER', 'quality'), ('GENERAL_MANAGER', 'inventory'), ('GENERAL_MANAGER', 'approvals'),
    ('GENERAL_MANAGER', 'configuration'), ('GENERAL_MANAGER', 'documents'), ('GENERAL_MANAGER', 'sample'),
    ('GENERAL_MANAGER', 'hardware'), ('GENERAL_MANAGER', 'operations'), ('GENERAL_MANAGER', 'mobile'),
    ('SALES', 'dashboard'), ('SALES', 'inbound'), ('SALES', 'inventory'), ('SALES', 'documents'), ('SALES', 'sample'),
    ('RECEIVER', 'dashboard'), ('RECEIVER', 'inbound'), ('RECEIVER', 'documents'), ('RECEIVER', 'hardware'), ('RECEIVER', 'mobile'),
    ('WORKSHOP_LEADER', 'dashboard'), ('WORKSHOP_LEADER', 'production'), ('WORKSHOP_LEADER', 'quality'),
    ('WORKSHOP_LEADER', 'approvals'), ('WORKSHOP_LEADER', 'sample'), ('WORKSHOP_LEADER', 'hardware'),
    ('WORKSHOP_LEADER', 'operations'), ('WORKSHOP_LEADER', 'mobile'),
    ('OPERATOR', 'dashboard'), ('OPERATOR', 'production'), ('OPERATOR', 'mobile'),
    ('QUALITY_INSPECTOR', 'dashboard'), ('QUALITY_INSPECTOR', 'quality'), ('QUALITY_INSPECTOR', 'inventory'),
    ('QUALITY_INSPECTOR', 'documents'), ('QUALITY_INSPECTOR', 'mobile'),
    ('WAREHOUSE_KEEPER', 'dashboard'), ('WAREHOUSE_KEEPER', 'inbound'), ('WAREHOUSE_KEEPER', 'inventory'),
    ('WAREHOUSE_KEEPER', 'documents'), ('WAREHOUSE_KEEPER', 'sample'), ('WAREHOUSE_KEEPER', 'hardware'),
    ('WAREHOUSE_KEEPER', 'operations'), ('WAREHOUSE_KEEPER', 'mobile'),
    ('FINANCE_STATISTICIAN', 'dashboard'), ('FINANCE_STATISTICIAN', 'approvals'), ('FINANCE_STATISTICIAN', 'configuration');
