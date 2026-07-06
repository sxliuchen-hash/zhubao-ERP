package com.zhubao.erp.business.domain;

import com.zhubao.erp.inventory.domain.StockStatus;

import java.math.BigDecimal;

public record AfterSalesReviewResult(
        String reviewCode,
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
        AfterSalesReviewDisposition disposition,
        String inspector,
        String finding,
        String evidenceCode
) {
}
