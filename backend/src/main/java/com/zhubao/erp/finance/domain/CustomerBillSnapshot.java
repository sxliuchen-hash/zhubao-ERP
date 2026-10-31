package com.zhubao.erp.finance.domain;

import com.zhubao.erp.common.error.BusinessException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 用已保存的原输入按同一舍入规则复算。不解析账单上的表达式。
 */
public final class CustomerBillSnapshot {

    private CustomerBillSnapshot() {
    }

    public static CustomerChargeSnapshot recompute(String deliveryId, List<CustomerDeliveryBillLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException("账单快照不完整，不能复算");
        }
        CustomerDeliveryBillLine first = lines.get(0);
        requireCurrentRule(first);
        for (CustomerDeliveryBillLine line : lines) {
            requireCurrentRule(line);
            if (!same(first.shippedQuantity(), line.shippedQuantity())
                    || !same(first.lossQuantity(), line.lossQuantity())
                    || !first.coverageUnit().equals(line.coverageUnit())) {
                throw new BusinessException("账单快照的交付覆盖不一致，不能复算");
            }
        }
        CustomerChargeCoverage coverage = new CustomerChargeCoverage(
                deliveryId, first.shippedQuantity(), first.coverageUnit(), first.lossQuantity());
        List<CustomerChargeLine> inputs = new ArrayList<>();
        for (CustomerDeliveryBillLine line : lines) {
            inputs.add(line.chargeLine());
        }
        CustomerChargeSnapshot again = CustomerChargeCalculator.calculate(coverage, inputs);
        if (again.lines().size() != lines.size()) {
            throw new BusinessException("已保存快照与复算不一致");
        }
        for (int i = 0; i < lines.size(); i++) {
            CustomerDeliveryBillLine stored = lines.get(i);
            CustomerChargeSnapshot.CustomerChargeResultLine result = again.lines().get(i);
            if (!same(stored.amount(), result.amount()) || !same(stored.billQuantity(), result.billQuantity())) {
                throw new BusinessException("已保存快照与复算不一致");
            }
        }
        return again;
    }

    private static void requireCurrentRule(CustomerDeliveryBillLine line) {
        if (line.quantityScale() == null || line.priceScale() == null || line.moneyScale() == null
                || line.rounding() == null || line.coverageUnit() == null || line.shippedQuantity() == null
                || line.method() == null) {
            throw new BusinessException("账单快照不完整，不能复算");
        }
        if (line.quantityScale() != CustomerChargeCalculator.QUANTITY_SCALE
                || line.priceScale() != CustomerChargeCalculator.PRICE_SCALE
                || line.moneyScale() != CustomerChargeCalculator.MONEY_SCALE
                || !CustomerChargeCalculator.ROUNDING.equals(line.rounding())) {
            throw new BusinessException("账单快照的舍入规则与当前规则不一致，不能直接复算");
        }
    }

    private static boolean same(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return left.compareTo(right) == 0;
    }
}
