package com.zhubao.erp.goldwork.domain;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockMeasurement;

import java.math.BigDecimal;

/** 金工申报计量。精度未核定前不能相加，也不能当成已入账库存。 */
public final class GoldworkMeasurement {

    private GoldworkMeasurement() {
    }

    public static BigDecimal declared(BigDecimal value, String zeroMessage) {
        if (value == null) {
            return null;
        }
        if (value.signum() == 0) {
            throw new BusinessException(zeroMessage);
        }
        if (value.signum() < 0) {
            throw new BusinessException("计量不能为负");
        }
        if (value.scale() > 6) {
            throw new BusinessException("金工计量精度尚未核定，不能截断后入账");
        }
        return value;
    }

    public static void refuseCombine(String leftPurity, String leftUnit, String rightPurity, String rightUnit) {
        if (!text(leftPurity).equals(text(rightPurity)) || !text(leftUnit).equals(text(rightUnit))) {
            throw new BusinessException("不同成色或单位不能直接相加");
        }
        StockMeasurement.rejectUndecidedGoldScale(BusinessKind.GOLDWORK);
    }

    public static OwnerType keepOwner(OwnerType owner) {
        return StockMeasurement.ownerAfterOutsourcing(owner);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
