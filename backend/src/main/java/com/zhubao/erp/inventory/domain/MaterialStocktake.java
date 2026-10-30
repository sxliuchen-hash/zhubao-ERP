package com.zhubao.erp.inventory.domain;

import java.math.BigDecimal;

/** 盘点快照。确认前不改原余额，确认后只追加一次差额。 */
public record MaterialStocktake(
        String stocktakeId,
        String countedAt,
        String itemId,
        String batchCode,
        String locationCode,
        BigDecimal bookQuantity,
        BigDecimal countedQuantity,
        String status,
        int version
) {
    public static final String OPEN = "OPEN";
    public static final String CONFIRMED = "CONFIRMED";
}
