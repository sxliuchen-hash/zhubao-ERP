package com.zhubao.erp.inventory.domain;

import java.math.BigDecimal;

/** 耗材费用事件。调整是追加，不覆盖原费用，也不因付款再生一笔。 */
public record ConsumableCostEvent(
        String eventId,
        String issueId,
        String kind,
        CompanyMaterialAmountState amountState,
        BigDecimal amount,
        BigDecimal quantity
) {
    public static final String COST = "COST";
    public static final String ADJUSTMENT = "ADJUSTMENT";
}
