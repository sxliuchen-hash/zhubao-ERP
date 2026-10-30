package com.zhubao.erp.business.domain;

import com.zhubao.erp.inventory.domain.StockMeasurement;

import java.math.BigDecimal;

/**
 * 一个执行目标下的实物袋。数量在袋上分配，不另建库存总账。
 */
public record ActivityBag(
        String id,
        String targetCode,
        String materialCode,
        String requirementVersion,
        String sourceKey,
        String parentId,
        boolean closed,
        BigDecimal quantity,
        StockMeasurement.Weight weight,
        int version
) {
    public ActivityBag {
        if (quantity == null || quantity.signum() < 0) {
            throw new com.zhubao.erp.common.error.BusinessException("袋数量不能为负");
        }
        weight = weight == null ? StockMeasurement.unknown() : weight;
    }

    public boolean activeLeaf() {
        return !closed;
    }
}
