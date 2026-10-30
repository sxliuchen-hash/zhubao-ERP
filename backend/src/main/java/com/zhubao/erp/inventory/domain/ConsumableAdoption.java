package com.zhubao.erp.inventory.domain;

import java.math.BigDecimal;

/**
 * 一次旧耗材结存接续。已接续的不再生成采购、应付或费用。
 */
public record ConsumableAdoption(
        String adoptionId,
        String oldItemCode,
        ConsumableAdoptionStatus status,
        String reason,
        String factoryItemId,
        String receiptId,
        BigDecimal quantity,
        CompanyMaterialAmountState amountState,
        BigDecimal amount,
        String specCode,
        String warehouseCode
) {
}
