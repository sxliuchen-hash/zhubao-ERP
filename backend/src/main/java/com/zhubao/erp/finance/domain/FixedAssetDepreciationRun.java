package com.zhubao.erp.finance.domain;

import java.util.List;

/**
 * 一次月折旧结果。算法只是当前的管理计算，不是全部会计或税务处理。
 */
public record FixedAssetDepreciationRun(
        String yearMonth,
        String algorithm,
        List<DepreciationEntry> entries,
        List<String> awaitingAssetCodes
) {
    public FixedAssetDepreciationRun(String yearMonth, String algorithm, List<DepreciationEntry> entries) {
        this(yearMonth, algorithm, entries, List.of());
    }
    public static final String MANAGEMENT_ALGORITHM = "USEFUL_MONTHS_WITH_FINAL_TAIL";
}
