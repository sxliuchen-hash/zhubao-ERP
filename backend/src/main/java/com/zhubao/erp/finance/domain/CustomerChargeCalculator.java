package com.zhubao.erp.finance.domain;

import com.zhubao.erp.common.error.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 有限计费。数量单价、基数扣数量、议定金额和有依据的补收或扣减。
 * 不执行脚本。按实发计费时，不会把损耗再自动扣一遍。
 */
public final class CustomerChargeCalculator {

    public static final int QUANTITY_SCALE = 3;
    public static final int PRICE_SCALE = 4;
    public static final int MONEY_SCALE = 2;
    public static final String ROUNDING = "HALF_UP";

    private CustomerChargeCalculator() {
    }

    public static CustomerChargeSnapshot calculate(CustomerChargeCoverage coverage, List<CustomerChargeLine> lines) {
        if (coverage == null) {
            throw new BusinessException("计费缺少交付覆盖");
        }
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException("计费至少要有一个项目");
        }
        List<CustomerChargeSnapshot.CustomerChargeResultLine> results = new ArrayList<>();
        BigDecimal total = money(BigDecimal.ZERO);
        for (CustomerChargeLine line : lines) {
            CustomerChargeSnapshot.CustomerChargeResultLine result = line(line);
            results.add(result);
            total = money(total.add(result.amount()));
        }
        return new CustomerChargeSnapshot(coverage, QUANTITY_SCALE, PRICE_SCALE, MONEY_SCALE, ROUNDING,
                List.copyOf(results), total);
    }

    private static CustomerChargeSnapshot.CustomerChargeResultLine line(CustomerChargeLine line) {
        return switch (line.method()) {
            case QUANTITY -> quantity(line);
            case BASE_MINUS_QUANTITY -> baseMinus(line);
            case AGREED_AMOUNT -> agreed(line);
            case ADJUSTMENT -> adjustment(line);
        };
    }

    private static CustomerChargeSnapshot.CustomerChargeResultLine quantity(CustomerChargeLine line) {
        requireUnit(line);
        BigDecimal quantity = required(line.quantity(), "计费数量无效");
        BigDecimal price = required(line.unitPrice(), "单价无效");
        if (quantity.signum() < 0 || price.signum() < 0) {
            throw new BusinessException("计费数量和单价不能为负");
        }
        BigDecimal amount = money(quantity.multiply(price));
        return result(line, quantity, price, amount, expression(quantity, line.quantityUnit(), price, amount));
    }

    private static CustomerChargeSnapshot.CustomerChargeResultLine baseMinus(CustomerChargeLine line) {
        requireUnit(line);
        BigDecimal base = required(line.baseQuantity(), "计费基数无效");
        BigDecimal exempt = required(line.exemptQuantity(), "免计数量无效");
        BigDecimal price = required(line.unitPrice(), "单价无效");
        if (exempt.compareTo(base) > 0) {
            throw new BusinessException("免计数量不能大于计费基数");
        }
        BigDecimal billable = base.subtract(exempt).setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
        BigDecimal amount = money(billable.multiply(price));
        String expression = "（" + plain(base) + line.quantityUnit() + " − " + plain(exempt) + line.quantityUnit()
                + "）× " + plain(price) + "元／" + line.quantityUnit() + "＝" + plain(amount) + "元";
        return result(line, billable, price, amount, expression);
    }

    private static CustomerChargeSnapshot.CustomerChargeResultLine agreed(CustomerChargeLine line) {
        if (line.basis().isEmpty()) {
            throw new BusinessException("议定金额必须保存约定说明");
        }
        BigDecimal amount = required(line.directAmount(), "议定金额无效");
        return result(line, null, null, amount, "议定 " + plain(amount) + "元");
    }

    private static CustomerChargeSnapshot.CustomerChargeResultLine adjustment(CustomerChargeLine line) {
        if (line.basis().isEmpty()) {
            throw new BusinessException("补收或扣减必须说明原因");
        }
        if (line.direction() == null) {
            throw new BusinessException("补收或扣减必须选择方向");
        }
        boolean byQuantity = line.quantity() != null || line.unitPrice() != null;
        boolean byAmount = line.directAmount() != null;
        if (byQuantity == byAmount) {
            throw new BusinessException("补收或扣减只能选择数量单价或直接金额");
        }
        BigDecimal amount;
        BigDecimal billQuantity = null;
        BigDecimal price = null;
        String expression;
        if (byAmount) {
            amount = line.directAmount();
            expression = "直接" + plain(amount) + "元";
        } else {
            requireUnit(line);
            billQuantity = required(line.quantity(), "计费数量无效");
            price = required(line.unitPrice(), "单价无效");
            amount = money(billQuantity.multiply(price));
            expression = expression(billQuantity, line.quantityUnit(), price, amount);
        }
        if (line.direction() == CustomerChargeDirection.DEDUCTION) {
            amount = amount.negate();
        }
        return result(line, billQuantity, price, money(amount), expression);
    }

    private static CustomerChargeSnapshot.CustomerChargeResultLine result(CustomerChargeLine line, BigDecimal quantity,
                                                                          BigDecimal price, BigDecimal amount,
                                                                          String expression) {
        return new CustomerChargeSnapshot.CustomerChargeResultLine(
                line.name(), line.targetCode(), line.method(), line.quantityUnit(), quantity, price, amount,
                line.basis(), expression);
    }

    private static String expression(BigDecimal quantity, String unit, BigDecimal price, BigDecimal amount) {
        return plain(quantity) + unit + " × " + plain(price) + "元／" + unit + "＝" + plain(amount) + "元";
    }

    private static void requireUnit(CustomerChargeLine line) {
        if (line.quantityUnit().isEmpty()) {
            throw new BusinessException("数量单位不能为空");
        }
    }

    private static BigDecimal required(BigDecimal value, String message) {
        if (value == null || value.signum() < 0) {
            throw new BusinessException(message);
        }
        return value;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
