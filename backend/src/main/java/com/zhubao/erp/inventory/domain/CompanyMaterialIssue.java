package com.zhubao.erp.inventory.domain;

import java.math.BigDecimal;

/**
 * 公司料领用。只把原批次转到车间或业务目标，权属仍是公司，不是消耗，也不是客户来料。
 */
public record CompanyMaterialIssue(
        String issueId,
        String itemId,
        String batchCode,
        String fromWarehouse,
        String workshopCode,
        BigDecimal quantity,
        BigDecimal returnedQuantity,
        BigDecimal consumedQuantity,
        String unit,
        StockMeasurement.Weight weight,
        String targetCode,
        int version
) {
}
