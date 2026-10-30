package com.zhubao.erp.finance.domain;

import com.zhubao.erp.common.error.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 一次交付的计费覆盖。实发数量只来自这里，收费行不能把它改大。
 * 损耗数量未知时保持空，不写成 0，也不会自动从实发里再扣一次。
 */
public record CustomerChargeCoverage(
        String deliveryId,
        BigDecimal shippedQuantity,
        String quantityUnit,
        BigDecimal lossQuantity
) {
    public CustomerChargeCoverage {
        if (deliveryId == null || deliveryId.isBlank()) {
            throw new BusinessException("计费缺少交付覆盖");
        }
        deliveryId = deliveryId.trim();
        if (shippedQuantity == null || shippedQuantity.signum() < 0) {
            throw new BusinessException("实发数量无效");
        }
        shippedQuantity = shippedQuantity.setScale(3, RoundingMode.HALF_UP);
        quantityUnit = quantityUnit == null ? "" : quantityUnit.trim();
        if (quantityUnit.isEmpty()) {
            throw new BusinessException("数量单位不能为空");
        }
        if (lossQuantity != null) {
            if (lossQuantity.signum() < 0) {
                throw new BusinessException("损耗数量无效");
            }
            lossQuantity = lossQuantity.setScale(3, RoundingMode.HALF_UP);
        }
    }
}
