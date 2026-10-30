package com.zhubao.erp.inventory.domain;

import com.zhubao.erp.common.error.BusinessException;

import java.math.BigDecimal;

/** 同一批次的数量相加。任一侧重量未知时，合计重量仍是未知，不用 0 顶上。 */
public final class CompanyMaterialBalances {

    private CompanyMaterialBalances() {
    }

    public static CompanyMaterialBalance merge(CompanyMaterialBalance current, CompanyMaterialBalance incoming) {
        if (current == null) {
            return incoming;
        }
        if (!current.unit().equals(incoming.unit())) {
            throw new BusinessException("同一批次的单位不能改变");
        }
        StockMeasurement.Weight weight = combine(current.weight(), incoming.weight());
        return new CompanyMaterialBalance(
                current.itemId(),
                current.batchCode(),
                current.warehouseCode(),
                current.quantity().add(incoming.quantity()),
                current.unit(),
                weight
        );
    }

    private static StockMeasurement.Weight combine(StockMeasurement.Weight left, StockMeasurement.Weight right) {
        if (left.state() != StockMeasurement.WeightState.KNOWN || right.state() != StockMeasurement.WeightState.KNOWN) {
            return StockMeasurement.unknown();
        }
        return StockMeasurement.known(left.value().add(right.value()));
    }

    public static BigDecimal knownAmount(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        if (amount.signum() == 0) {
            throw new BusinessException("未知金额不能写成 0");
        }
        if (amount.signum() < 0) {
            throw new BusinessException("金额不能为负");
        }
        if (amount.scale() > 2) {
            throw new BusinessException("金额最多两位小数");
        }
        return amount;
    }
}
