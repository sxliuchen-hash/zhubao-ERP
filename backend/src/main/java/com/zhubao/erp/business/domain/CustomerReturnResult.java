package com.zhubao.erp.business.domain;

import com.zhubao.erp.inventory.domain.StockStatus;

import java.math.BigDecimal;

public record CustomerReturnResult(
        String returnCode,
        String originalShipmentCode,
        String workOrderCode,
        String ledgerCode,
        String fromWarehouseCode,
        String toWarehouseCode,
        StockStatus fromStockStatus,
        StockStatus toStockStatus,
        BigDecimal quantity,
        BigDecimal weight,
        String returnLogisticsNo,
        String reason,
        String operator,
        String evidenceCode
) {
}
