package com.zhubao.erp.business.domain;

import com.zhubao.erp.inventory.domain.StockStatus;

import java.math.BigDecimal;

public record QualityFreezeResult(
        String qualityCaseCode,
        String workOrderCode,
        String ledgerCode,
        String fromWarehouseCode,
        String toWarehouseCode,
        StockStatus fromStockStatus,
        StockStatus toStockStatus,
        BigDecimal quantity,
        BigDecimal weight,
        String process,
        String inspector,
        String evidenceCode
) {
}
