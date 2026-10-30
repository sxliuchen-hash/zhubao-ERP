package com.zhubao.erp.finance.domain;

import com.zhubao.erp.common.error.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 一条收费输入。数量计费不会读取免计数量；免计只在基数扣数量时生效。
 */
public record CustomerChargeLine(
        String name,
        String targetCode,
        CustomerChargeMethod method,
        String quantityUnit,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal baseQuantity,
        BigDecimal exemptQuantity,
        BigDecimal directAmount,
        CustomerChargeDirection direction,
        String basis
) {
    public CustomerChargeLine {
        name = required(name, "收费项目不能为空");
        targetCode = targetCode == null ? "" : targetCode.trim();
        if (method == null) {
            throw new BusinessException("计费方式不能为空");
        }
        quantityUnit = quantityUnit == null ? "" : quantityUnit.trim();
        basis = basis == null ? "" : basis.trim();
        quantity = scaleQuantity(quantity);
        unitPrice = scalePrice(unitPrice);
        baseQuantity = scaleQuantity(baseQuantity);
        exemptQuantity = scaleQuantity(exemptQuantity);
        directAmount = scaleMoney(directAmount);
    }

    private static String required(String value, String message) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            throw new BusinessException(message);
        }
        return text;
    }

    private static BigDecimal scaleQuantity(BigDecimal value) {
        return value == null ? null : value.setScale(3, RoundingMode.HALF_UP);
    }

    private static BigDecimal scalePrice(BigDecimal value) {
        return value == null ? null : value.setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal scaleMoney(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
