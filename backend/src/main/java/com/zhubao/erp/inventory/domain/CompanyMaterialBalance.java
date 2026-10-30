package com.zhubao.erp.inventory.domain;

import java.math.BigDecimal;

/**
 * 公司物品在某一批次和仓库的数量。只由入库命令改写，重量未知时不是 0。
 */
public record CompanyMaterialBalance(
        String itemId,
        String batchCode,
        String warehouseCode,
        BigDecimal quantity,
        String unit,
        StockMeasurement.Weight weight
) {
}
