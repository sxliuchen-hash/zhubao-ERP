package com.zhubao.erp.inventory.domain;

import java.math.BigDecimal;

/** 按原领用退回。单位和批次不变，退回后仍是公司实物。 */
public record CompanyMaterialReturn(
        String returnId,
        String issueId,
        BigDecimal quantity,
        String unit,
        StockMeasurement.Weight weight
) {
}
