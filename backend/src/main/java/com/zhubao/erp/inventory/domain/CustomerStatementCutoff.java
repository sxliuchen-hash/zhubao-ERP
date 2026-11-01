package com.zhubao.erp.inventory.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 某一截止时点的客户数量对账。账单范围未核定，不放金额。在厂和外协拆不开时标待核，不填 0。
 */
public record CustomerStatementCutoff(
        long customerId,
        Instant cutoff,
        BigDecimal receivedQuantity,
        BigDecimal shippedQuantity,
        BigDecimal lossQuantity,
        String lossWeight,
        String factoryAndOutside,
        String bills,
        List<String> afterCutoff,
        List<String> pending,
        String exportText,
        List<QuantityGroup> quantities
) {
    public CustomerStatementCutoff {
        afterCutoff = afterCutoff == null ? List.of() : List.copyOf(afterCutoff);
        pending = pending == null ? List.of() : List.copyOf(pending);
        quantities = List.copyOf(quantities);
    }

    public record QuantityGroup(String materialCode, String sourceCode, String unit,
                                BigDecimal receivedQuantity, BigDecimal shippedQuantity, BigDecimal lossQuantity) {}
}
