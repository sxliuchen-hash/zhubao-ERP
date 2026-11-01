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

    public static StockMeasurement.Weight remainder(StockMeasurement.Weight source, StockMeasurement.Weight moved) {
        if (moved.state() != StockMeasurement.WeightState.KNOWN) {
            return StockMeasurement.unknown();
        }
        if (source.state() != StockMeasurement.WeightState.KNOWN) {
            throw new BusinessException("来源重量未知，不能按已知重量领出");
        }
        if (moved.value().compareTo(source.value()) > 0) {
            throw new BusinessException("领用重量不能超过批次重量");
        }
        return StockMeasurement.known(source.value().subtract(moved.value()));
    }

    public static void assertFullMovementWeight(CompanyMaterialBalance source, BigDecimal quantity,
                                                 StockMeasurement.Weight moved) {
        if (source.quantity().compareTo(quantity) == 0
                && source.weight().state() == StockMeasurement.WeightState.KNOWN
                && (moved.state() != StockMeasurement.WeightState.KNOWN
                || moved.value().compareTo(source.weight().value()) != 0)) {
            throw new BusinessException("全量领退的实称重量与来源不符，请先复称核对");
        }
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
