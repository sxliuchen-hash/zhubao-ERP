-- M3.137 标签模板与打印机设置表。
-- 支持用户自定义标签模板（品牌抬头、字段列表、脚注来源）和打印机设置（尺寸、DPI、打印机名、份数）。
-- fields_json 存字段定义列表，printer_setting_json 存打印机设置；label_type 内 is_default 唯一（应用层保证）。
-- template_code 全局唯一。seed 四类标签（母单/子工单/出货/返工）的默认模板基线。

CREATE TABLE IF NOT EXISTS sys_label_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    template_code VARCHAR(80) NOT NULL,
    template_name VARCHAR(120) NOT NULL,
    label_type VARCHAR(40) NOT NULL,
    brand_title VARCHAR(120),
    fields_json TEXT,
    footer_left_source VARCHAR(80),
    footer_right_source VARCHAR(80),
    is_default TINYINT(1) NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    printer_setting_json TEXT,
    remark VARCHAR(255),
    created_by VARCHAR(80),
    created_at VARCHAR(40),
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_label_template_code (template_code)
);

INSERT INTO sys_label_template (
    template_code, template_name, label_type, brand_title, fields_json,
    footer_left_source, footer_right_source, is_default, enabled, printer_setting_json, remark, created_by, created_at
) VALUES
    ('MOTHER-DEFAULT', '母单默认模板', 'MOTHER', '珠宝来料加工系统',
     '[{"label":"客户","source":"customer","sortOrder":1},{"label":"物料","source":"goodsName","sortOrder":2},{"label":"数量","source":"quantity","sortOrder":3},{"label":"重量","source":"weight","sortOrder":4},{"label":"收货日","source":"inboundDate","sortOrder":5}]',
     'express', 'receiver', 1, 1,
     '{"widthMm":60,"heightMm":40,"dpi":203,"printerName":"","copies":1}', '一期评审 seed', 'system', '2026-07-09T10:00:00'),
    ('WORK_ORDER-DEFAULT', '子工单默认模板', 'WORK_ORDER', '珠宝来料加工系统',
     '[{"label":"客户","source":"customer","sortOrder":1},{"label":"物料","source":"goodsName","sortOrder":2},{"label":"数量","source":"quantity","sortOrder":3}]',
     '', '', 1, 1,
     '{"widthMm":60,"heightMm":40,"dpi":203,"printerName":"","copies":1}', '一期评审 seed', 'system', '2026-07-09T10:00:00'),
    ('OUTBOUND-DEFAULT', '出货默认模板', 'OUTBOUND', '珠宝来料加工系统',
     '[{"label":"客户","source":"customer","sortOrder":1},{"label":"数量","source":"quantity","sortOrder":2},{"label":"重量","source":"weight","sortOrder":3}]',
     'express', 'receiver', 1, 1,
     '{"widthMm":60,"heightMm":40,"dpi":203,"printerName":"","copies":1}', '一期评审 seed', 'system', '2026-07-09T10:00:00'),
    ('REWORK-DEFAULT', '返工默认模板', 'REWORK', '珠宝来料加工系统',
     '[{"label":"客户","source":"customer","sortOrder":1},{"label":"物料","source":"goodsName","sortOrder":2},{"label":"数量","source":"quantity","sortOrder":3}]',
     '', '', 1, 1,
     '{"widthMm":60,"heightMm":40,"dpi":203,"printerName":"","copies":1}', '一期评审 seed', 'system', '2026-07-09T10:00:00');
