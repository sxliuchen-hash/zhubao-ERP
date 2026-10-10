package com.zhubao.erp.ops.domain;

import java.util.List;

/**
 * 升级前的只读结论。detectedBaseline 是连续探针能证明的最高版本，不是最新脚本版本。
 */
public record UpgradePlan(
        int detectedBaseline,
        boolean structureGap,
        boolean databaseEmpty,
        List<MigrationRecord> applied,
        List<MigrationScript> pending,
        List<String> problems
) {
}
