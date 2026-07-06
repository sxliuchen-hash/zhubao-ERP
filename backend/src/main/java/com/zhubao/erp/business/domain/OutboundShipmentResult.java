package com.zhubao.erp.business.domain;

import com.zhubao.erp.inventory.domain.StockStatus;

import java.math.BigDecimal;

public record OutboundShipmentResult(
        String shipmentCode,
        String workOrderCode,
        String ledgerCode,
        String fromWarehouseCode,
        String toWarehouseCode,
        StockStatus fromStockStatus,
        StockStatus toStockStatus,
        BigDecimal quantity,
        BigDecimal weight,
        String logisticsNo,
        String operator,
        String evidenceCode
) {
}
