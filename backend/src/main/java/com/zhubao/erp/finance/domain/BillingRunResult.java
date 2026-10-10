package com.zhubao.erp.finance.domain;

import java.util.List;

/**
 * 一次账单生成的结果。已入账的不重复生成。
 */
public record BillingRunResult(
        String yearMonth,
        int createdCount,
        List<ReceivableLine> createdLines,
        List<UnpricedItem> unpriced
) {
    public BillingRunResult {
        createdLines = List.copyOf(createdLines);
        unpriced = List.copyOf(unpriced);
    }
}
