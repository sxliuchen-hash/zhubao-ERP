-- 将仍保持系统旧基线的四类内置标签改为 40×30mm、300dpi。
-- 字段、脚注和打印设置分别比较旧值；用户改过的部分及自定义模板保持原样。

UPDATE sys_label_template
SET fields_json = '[{"label":"验证码","source":"verificationCode","sortOrder":1},{"label":"客户","source":"customer","sortOrder":2},{"label":"加工要求","source":"processingRequirement","sortOrder":3},{"label":"期限","source":"dueDate","sortOrder":4}]'
WHERE template_code IN ('MOTHER-DEFAULT', 'WORK_ORDER-DEFAULT')
  AND label_type IN ('MOTHER', 'WORK_ORDER')
  AND created_by = 'system'
  AND fields_json = '[{"label":"验证码","source":"verificationCode","sortOrder":1},{"label":"客户","source":"customer","sortOrder":2},{"label":"加工要求","source":"processingRequirement","sortOrder":3},{"label":"期限","source":"dueDate","sortOrder":4},{"label":"单号","source":"code","sortOrder":5},{"label":"物料","source":"goodsName","sortOrder":6},{"label":"数量","source":"quantity","sortOrder":7},{"label":"重量","source":"weight","sortOrder":8}]';

UPDATE sys_label_template
SET fields_json = '[{"label":"客户","source":"customer","sortOrder":1},{"label":"数量","source":"quantity","sortOrder":2}]'
WHERE template_code = 'OUTBOUND-DEFAULT' AND label_type = 'OUTBOUND' AND created_by = 'system'
  AND fields_json = '[{"label":"客户","source":"customer","sortOrder":1},{"label":"数量","source":"quantity","sortOrder":2},{"label":"重量","source":"weight","sortOrder":3}]';

UPDATE sys_label_template
SET fields_json = '[{"label":"客户","source":"customer","sortOrder":1},{"label":"数量","source":"quantity","sortOrder":2}]'
WHERE template_code = 'REWORK-DEFAULT' AND label_type = 'REWORK' AND created_by = 'system'
  AND fields_json = '[{"label":"客户","source":"customer","sortOrder":1},{"label":"物料","source":"goodsName","sortOrder":2},{"label":"数量","source":"quantity","sortOrder":3}]';

UPDATE sys_label_template
SET printer_setting_json = '{"widthMm":40,"heightMm":30,"dpi":300,"printerName":"","copies":1}'
WHERE template_code IN ('MOTHER-DEFAULT', 'WORK_ORDER-DEFAULT', 'OUTBOUND-DEFAULT', 'REWORK-DEFAULT')
  AND created_by = 'system'
  AND printer_setting_json = '{"widthMm":60,"heightMm":40,"dpi":203,"printerName":"","copies":1}';

UPDATE sys_label_template
SET footer_left_source = ''
WHERE template_code IN ('MOTHER-DEFAULT', 'OUTBOUND-DEFAULT')
  AND created_by = 'system' AND footer_left_source = 'express';

UPDATE sys_label_template
SET footer_right_source = ''
WHERE template_code IN ('MOTHER-DEFAULT', 'OUTBOUND-DEFAULT')
  AND created_by = 'system' AND footer_right_source = 'receiver';

-- 数据迁移没有天然结构探针，最后建立独立完成标记供基线检查使用。
CREATE TABLE IF NOT EXISTS sys_label_default_revision (
    revision_code VARCHAR(20) NOT NULL PRIMARY KEY
);

INSERT INTO sys_label_default_revision (revision_code)
SELECT 'V56' WHERE NOT EXISTS (
    SELECT 1 FROM sys_label_default_revision WHERE revision_code = 'V56'
);
