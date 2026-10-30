package com.zhubao.erp.inventory.domain;

import java.math.BigDecimal;

/** 工具借出。只改保管，不改公司所有权，也不计耗用。 */
public record ToolLoan(
        String loanId,
        String itemId,
        String holderId,
        String holderName,
        BigDecimal quantity,
        BigDecimal returnedQuantity,
        String unit,
        String pieceCode,
        String warehouseCode,
        int version
) {
    public BigDecimal outstanding() {
        return quantity.subtract(returnedQuantity);
    }
}
