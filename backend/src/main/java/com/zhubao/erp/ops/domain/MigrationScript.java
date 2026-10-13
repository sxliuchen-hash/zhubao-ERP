package com.zhubao.erp.ops.domain;

/**
 * 单个历史迁移及其结构探针。校验和按原始字节计算，执行文本可包含定向语法兼容修正。
 */
public record MigrationScript(
        int version,
        String fileName,
        String checksum,
        String sql,
        SchemaProbe probe
) {
}
