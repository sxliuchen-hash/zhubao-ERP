package com.zhubao.erp.finance.domain;

import com.zhubao.erp.common.error.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** 本次交付中同一目标、同一单位的真实份额，不跨单位求数量合计。 */
public record CustomerChargeSource(String targetCode, String quantityUnit, BigDecimal quantity) {
    public CustomerChargeSource {
        if (targetCode == null || targetCode.isBlank() || quantityUnit == null || quantityUnit.isBlank()
                || quantity == null || quantity.signum() < 0) {
            throw new BusinessException("交付计费来源无效");
        }
        targetCode = targetCode.trim();
        quantityUnit = quantityUnit.trim();
        quantity = quantity.setScale(3, RoundingMode.HALF_UP);
    }
}
