package com.zhubao.erp.finance.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 可复算的计费快照。实发数量保持覆盖上的原值，行金额按行舍入后再合计，负净额不截成 0。
 */
public record CustomerChargeSnapshot(
        CustomerChargeCoverage coverage,
        int quantityScale,
        int priceScale,
        int moneyScale,
        String rounding,
        List<CustomerChargeResultLine> lines,
        BigDecimal totalAmount
) {
    public record CustomerChargeResultLine(
            String name,
            String targetCode,
            CustomerChargeMethod method,
            String quantityUnit,
            BigDecimal billQuantity,
            BigDecimal unitPrice,
            BigDecimal amount,
            String basis,
            String expression
    ) {
    }
}
