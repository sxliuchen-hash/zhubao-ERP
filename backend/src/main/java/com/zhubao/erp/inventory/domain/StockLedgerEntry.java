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
        String remark,
        String sourceLedgerCode
) {
    /** 普通转移没有原流水关联。 */
    public StockLedgerEntry(
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
        this(code, bizTime, documentType, documentCode, from, to, quantity, weight, remark, null);
    }
}
