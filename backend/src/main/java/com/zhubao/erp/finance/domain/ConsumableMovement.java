package com.zhubao.erp.finance.domain;

import java.math.BigDecimal;

/**
 * 消耗品采购或领用。采购形成供应商应付，领用计入当月成本。
 */
public record ConsumableMovement(
        String code,
        String itemCode,
        String direction,
        BigDecimal quantity,
        BigDecimal amount,
        String supplier,
        Long customerId,
        String yearMonth,
        String payableCode,
        String operator,
        String createdAt
) {
    public ConsumableMovement {
        supplier = supplier == null ? "" : supplier;
        payableCode = payableCode == null ? "" : payableCode;
    }
}
