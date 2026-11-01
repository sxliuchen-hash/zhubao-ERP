package com.zhubao.erp.business.domain;

import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockMeasurement;

import java.math.BigDecimal;

/**
 * 一次数量处置。交出、损耗、报废、退回和待核互斥，合计不能超过当前可处理量。
 * 未知重量用 unknown，不能传 0。
 */
public record QuantityDispositionCommand(
        String requestId,
        String bagId,
        int bagVersion,
        BigDecimal handoff,
        BigDecimal loss,
        BigDecimal scrap,
        BigDecimal returned,
        BigDecimal pending,
        String attachmentCode,
        StockMeasurement.Weight exitWeight,
        StockDimension stockFrom,
        BigDecimal workQuantity
) {
    public QuantityDispositionCommand(String requestId, String bagId, int bagVersion, BigDecimal handoff, BigDecimal loss,
                                      BigDecimal scrap, BigDecimal returned, BigDecimal pending, String attachmentCode,
                                      StockMeasurement.Weight exitWeight, StockDimension stockFrom) {
        this(requestId, bagId, bagVersion, handoff, loss, scrap, returned, pending, attachmentCode, exitWeight, stockFrom, null);
    }
}
