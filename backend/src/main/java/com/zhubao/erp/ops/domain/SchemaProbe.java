package com.zhubao.erp.ops.domain;

/**
 * 旧库基线探针。只判断结构或指定数据是否已经存在，不代替迁移脚本。
 */
public record SchemaProbe(
        Kind kind,
        String tableName,
        String columnName,
        String expectedValue
) {
    public enum Kind {
        TABLE,
        COLUMN,
        ROW
    }
}
