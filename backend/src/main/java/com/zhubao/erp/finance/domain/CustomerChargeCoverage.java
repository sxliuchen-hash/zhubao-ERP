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
        BigDecimal lossQuantity,
        List<CustomerChargeSource> sources
) {
    public CustomerChargeCoverage(String deliveryId, BigDecimal shippedQuantity, String quantityUnit, BigDecimal lossQuantity) {
        this(deliveryId, shippedQuantity, quantityUnit, lossQuantity, List.of());
    }

    public CustomerChargeCoverage {
        if (deliveryId == null || deliveryId.isBlank()) {
            throw new BusinessException("计费缺少交付覆盖");
        }
        deliveryId = deliveryId.trim();
        sources = sources == null ? List.of() : List.copyOf(sources);
        boolean mixed = sources.stream().map(CustomerChargeSource::quantityUnit).distinct().count() > 1;
        if (!sources.isEmpty()) {
            quantityUnit = mixed ? "" : sources.get(0).quantityUnit();
            shippedQuantity = mixed ? null : sources.stream().map(CustomerChargeSource::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        if (!mixed && (shippedQuantity == null || shippedQuantity.signum() < 0)) {
            throw new BusinessException("实发数量无效");
        }
        shippedQuantity = shippedQuantity == null ? null : shippedQuantity.setScale(3, RoundingMode.HALF_UP);
        quantityUnit = quantityUnit == null ? "" : quantityUnit.trim();
        if (!mixed && quantityUnit.isEmpty()) {
            throw new BusinessException("数量单位不能为空");
        }
        if (lossQuantity != null) {
            if (mixed) {
                throw new BusinessException("混合单位损耗须按项目分别核定");
            }
            if (lossQuantity.signum() < 0) {
                throw new BusinessException("损耗数量无效");
            }
            lossQuantity = lossQuantity.setScale(3, RoundingMode.HALF_UP);
        }
    }
}
