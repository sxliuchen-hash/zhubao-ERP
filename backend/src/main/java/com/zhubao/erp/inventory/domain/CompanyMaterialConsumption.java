package com.zhubao.erp.inventory.domain;

import java.math.BigDecimal;

/**
 * 一次领用的实际去向。耗用费用只关联目标，不自动变成客户收费。
 */
public record CompanyMaterialConsumption(
        String consumptionId,
        String issueId,
        BigDecimal consumedQuantity,
        BigDecimal returnedQuantity,
        BigDecimal keptQuantity,
        BigDecimal scrapQuantity,
        String unit,
        CompanyMaterialAmountState amountState,
        BigDecimal amount,
        String targetCode
) {
}
