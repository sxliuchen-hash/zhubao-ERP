package com.zhubao.erp.inventory.domain;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.common.error.BusinessException;

import java.math.BigDecimal;

/**
 * 三业务共用的计量约定。未知重量不是 0。金工精度未核定前不截成三位后入账。
 */
public final class StockMeasurement {

    public enum WeightState {
        KNOWN,
        UNKNOWN
    }

    public record Weight(WeightState state, BigDecimal value) {
    }

    private StockMeasurement() {
    }

    public static Weight known(BigDecimal value) {
        if (value == null) {
            return unknown();
        }
        if (value.signum() < 0) {
            throw new BusinessException("重量不能为负");
        }
        return new Weight(WeightState.KNOWN, value);
    }

    public static Weight unknown() {
        return new Weight(WeightState.UNKNOWN, null);
    }

    public static void assertSameUnit(String left, String right) {
        if (!unit(left).equals(unit(right))) {
            throw new BusinessException("不能把不同单位的数量加在一起");
        }
    }

    public static OwnerType ownerAfterOutsourcing(OwnerType owner) {
        if (owner == null) {
            throw new BusinessException("缺少物料权属");
        }
        return owner;
    }

    public static BigDecimal processingScale(BigDecimal value) {
        if (value == null) {
            throw new BusinessException("缺少数量或重量");
        }
        if (value.scale() > 3) {
            throw new BusinessException("普通库存最多三位小数，不能截断后入账");
        }
        return value;
    }

    public static void rejectUndecidedGoldScale(BusinessKind kind) {
        if (kind == BusinessKind.GOLDWORK) {
            throw new BusinessException("金工计量精度尚未核定，不能按现有三位库存截断后入账");
        }
    }

    private static String unit(String value) {
        return value == null ? "" : value.trim();
    }
}
