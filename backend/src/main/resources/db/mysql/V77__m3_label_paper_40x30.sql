-- 厂内标签纸是 40×30 mm。仍停在旧基线 60×40 的模板和打印队列改过来，已经改成其他尺寸的不动。

UPDATE sys_label_template
SET printer_setting_json = REPLACE(REPLACE(printer_setting_json, '"widthMm":60', '"widthMm":40'), '"heightMm":40', '"heightMm":30')
WHERE printer_setting_json LIKE '%"widthMm":60%'
  AND printer_setting_json LIKE '%"heightMm":40%';

UPDATE biz_print_queue
SET width_mm = 40, height_mm = 30
WHERE width_mm = 60 AND height_mm = 40;

INSERT INTO sys_label_default_revision (revision_code)
SELECT 'V77' WHERE NOT EXISTS (
    SELECT 1 FROM sys_label_default_revision WHERE revision_code = 'V77'
);
