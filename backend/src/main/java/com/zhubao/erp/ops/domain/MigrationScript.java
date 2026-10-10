package com.zhubao.erp.ops.domain;

/**
 * 单个历史迁移及其结构探针。校验和按文件原始字节计算。
 */
public record MigrationScript(
        int version,
        String fileName,
        String checksum,
        String sql,
        SchemaProbe probe
) {
}
