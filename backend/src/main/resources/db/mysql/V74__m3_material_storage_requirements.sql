-- 材质档案及来源快照；NULL 保留历史未记录语义，不回填历史单据。
ALTER TABLE biz_material_catalog ADD COLUMN storage_conditions VARCHAR(80) NULL;
ALTER TABLE biz_material_catalog ADD COLUMN storage_instruction VARCHAR(500) NULL;
ALTER TABLE biz_receipt_line ADD COLUMN storage_conditions VARCHAR(80) NULL;
ALTER TABLE biz_receipt_line ADD COLUMN storage_instruction VARCHAR(500) NULL;
ALTER TABLE biz_child_line_allocation ADD COLUMN storage_conditions VARCHAR(80) NULL;
ALTER TABLE biz_child_line_allocation ADD COLUMN storage_instruction VARCHAR(500) NULL;
ALTER TABLE inv_stock_document_line ADD COLUMN storage_conditions VARCHAR(80) NULL;
ALTER TABLE inv_stock_document_line ADD COLUMN storage_instruction VARCHAR(500) NULL;
CREATE TABLE biz_material_storage_change (
    change_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    material_code VARCHAR(64) NOT NULL,
    previous_conditions VARCHAR(80) NULL,
    previous_instruction VARCHAR(500) NULL,
    current_conditions VARCHAR(80) NULL,
    current_instruction VARCHAR(500) NULL,
    changed_at VARCHAR(40) NOT NULL
);
CREATE INDEX idx_material_storage_change ON biz_material_storage_change(material_code, change_id);
