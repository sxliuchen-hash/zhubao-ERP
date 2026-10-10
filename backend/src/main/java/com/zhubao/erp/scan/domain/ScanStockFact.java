package com.zhubao.erp.scan.domain;

import java.math.BigDecimal;

/** 一笔现有库存余额，查询不会改写它。 */
public record ScanStockFact(
        String materialCode,
        String warehouseCode,
        String ownerType,
        Long ownerCustomerId,
        String workOrderCode,
        String stockStatus,
        BigDecimal quantity,
        BigDecimal weight
) {
}
