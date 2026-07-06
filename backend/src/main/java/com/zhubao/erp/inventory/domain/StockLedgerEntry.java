package com.zhubao.erp.inventory.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record StockLedgerEntry(
        String code,
        Instant bizTime,
        String documentType,
        String documentCode,
        StockDimension from,
        StockDimension to,
        BigDecimal quantity,
        BigDecimal weight,
        String remark
) {
}
