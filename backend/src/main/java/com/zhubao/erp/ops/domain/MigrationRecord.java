package com.zhubao.erp.ops.domain;

/**
 * 已登记的迁移。APPLYING 和 FAILED 都表示不能继续往后执行。
 */
public record MigrationRecord(
        int version,
        String scriptName,
        String checksum,
        UpgradeStatus status,
        String startedAt,
        String finishedAt,
        String errorMessage
) {
}
