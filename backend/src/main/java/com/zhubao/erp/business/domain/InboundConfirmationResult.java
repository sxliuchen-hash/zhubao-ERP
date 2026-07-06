package com.zhubao.erp.business.domain;

import com.zhubao.erp.inventory.domain.StockStatus;

import java.math.BigDecimal;

public record InboundConfirmationResult(
        String motherOrderCode,
        String ledgerCode,
        String warehouseCode,
        StockStatus stockStatus,
        BigDecimal quantity,
        BigDecimal weight,
        String operator,
        String evidenceCode
) {
}
