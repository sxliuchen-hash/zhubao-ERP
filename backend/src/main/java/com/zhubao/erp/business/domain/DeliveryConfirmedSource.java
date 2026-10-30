package com.zhubao.erp.business.domain;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockMeasurement;

import java.math.BigDecimal;

/**
 * 已确认交付的一袋出库事实。库存只在这一行扣减，汇总单不再另扣。
 */
public record DeliveryConfirmedSource(
        String deliveryId,
        int lineNo,
        String bagId,
        String outboundCode,
        String ledgerCode,
        BigDecimal quantity,
        BigDecimal weight
) {
    public DeliveryConfirmedSource {
        if (deliveryId == null || !deliveryId.matches("^FH-\\d{8}-\\d{4}$")) {
            throw new BusinessException("交付单号无效");
        }
        if (lineNo < 1) {
            throw new BusinessException("交付袋序不正确");
        }
        if (bagId == null || bagId.isBlank() || bagId.length() > 40) {
            throw new BusinessException("缺少交付袋");
        }
        bagId = bagId.trim();
        if (outboundCode == null || outboundCode.isBlank() || outboundCode.length() > 40) {
            throw new BusinessException("出库单号无效");
        }
        outboundCode = outboundCode.trim();
        if (ledgerCode == null || ledgerCode.isBlank() || ledgerCode.length() > 80) {
            throw new BusinessException("库存流水无效");
        }
        ledgerCode = ledgerCode.trim();
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessException("交付数量必须为正");
        }
        quantity = StockMeasurement.processingScale(quantity);
        if (weight == null || weight.signum() <= 0) {
            throw new BusinessException("需要本份额的实际重量后才能转移或发货");
        }
        weight = StockMeasurement.processingScale(weight);
    }
}
