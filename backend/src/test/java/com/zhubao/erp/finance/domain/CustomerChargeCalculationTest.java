package com.zhubao.erp.finance.domain;

import com.zhubao.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerChargeCalculationTest {

    @Test
    void 按实发收费不自动再减损耗() {
        CustomerChargeSnapshot snapshot = CustomerChargeCalculator.calculate(
                coverage("462", new BigDecimal("38")),
                List.of(quantity("加工费", "462", "2")));
        assertThat(snapshot.coverage().shippedQuantity()).isEqualByComparingTo("462");
        assertThat(snapshot.coverage().lossQuantity()).isEqualByComparingTo("38");
        assertThat(snapshot.totalAmount()).isEqualByComparingTo("924.00");
        assertThat(snapshot.lines()).singleElement().satisfies(line -> {
            assertThat(line.billQuantity()).isEqualByComparingTo("462.000");
            assertThat(line.amount()).isEqualByComparingTo("924.00");
            assertThat(line.expression()).isEqualTo("462颗 × 2元／颗＝924元");
        });
        assertThat(snapshot.quantityScale()).isEqualTo(3);
        assertThat(snapshot.priceScale()).isEqualTo(4);
        assertThat(snapshot.moneyScale()).isEqualTo(2);
        assertThat(snapshot.rounding()).isEqualTo("HALF_UP");
    }

    @Test
    void 基数扣数量和显式赔偿可以分开计算() {
        CustomerChargeSnapshot base = CustomerChargeCalculator.calculate(coverage("462", null), List.of(
                new CustomerChargeLine("加工费", "MD-1", CustomerChargeMethod.BASE_MINUS_QUANTITY, "颗",
                        null, new BigDecimal("2"), new BigDecimal("500"), new BigDecimal("38"), null, null, "免计损耗")));
        assertThat(base.totalAmount()).isEqualByComparingTo("924.00");
        assertThat(base.coverage().lossQuantity()).isNull();
        assertThat(base.lines().get(0).expression()).isEqualTo("（500颗 − 38颗）× 2元／颗＝924元");

        CustomerChargeSnapshot mixed = CustomerChargeCalculator.calculate(coverage("462", new BigDecimal("38")), List.of(
                quantity("加工费", "500", "2"),
                adjustment("损耗赔偿", "38", "5", CustomerChargeDirection.DEDUCTION, "另扣38颗每颗5元")));
        assertThat(mixed.totalAmount()).isEqualByComparingTo("810.00");
        assertThat(mixed.coverage().shippedQuantity()).isEqualByComparingTo("462");

        CustomerChargeSnapshot extras = CustomerChargeCalculator.calculate(coverage("462", null), List.of(
                quantity("加工费", "462", "2"),
                new CustomerChargeLine("加急", "MD-1", CustomerChargeMethod.ADJUSTMENT, "",
                        null, null, null, null, new BigDecimal("50"), CustomerChargeDirection.SURCHARGE, "加急"),
                new CustomerChargeLine("折让", "MD-1", CustomerChargeMethod.ADJUSTMENT, "",
                        null, null, null, null, new BigDecimal("20"), CustomerChargeDirection.DEDUCTION, "折让")));
        assertThat(extras.totalAmount()).isEqualByComparingTo("954.00");

        CustomerChargeSnapshot agreed = CustomerChargeCalculator.calculate(coverage("462", null), List.of(
                new CustomerChargeLine("加工费", "MD-1", CustomerChargeMethod.AGREED_AMOUNT, "",
                        null, null, null, null, new BigDecimal("900"), null, "说明里写了462*2也不执行")));
        assertThat(agreed.totalAmount()).isEqualByComparingTo("900.00");
        assertThat(agreed.lines().get(0).expression()).isEqualTo("议定 900元");
    }

    @Test
    void 多个收费项目不把实发数量加总() {
        CustomerChargeSnapshot snapshot = CustomerChargeCalculator.calculate(coverage("300", null), List.of(
                quantity("雕刻", "300", "2"),
                new CustomerChargeLine("抛光", "MD-1", CustomerChargeMethod.QUANTITY, "颗",
                        new BigDecimal("300"), new BigDecimal("3"), null, null, null, null, "")));
        assertThat(snapshot.coverage().shippedQuantity()).isEqualByComparingTo("300");
        assertThat(snapshot.totalAmount()).isEqualByComparingTo("1500.00");
        assertThat(snapshot.lines()).extracting(CustomerChargeSnapshot.CustomerChargeResultLine::billQuantity)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("300"), new BigDecimal("300"));
    }

    @Test
    void 扣减大于收费时保留负净额() {
        CustomerChargeSnapshot snapshot = CustomerChargeCalculator.calculate(coverage("10", null), List.of(
                quantity("加工费", "10", "1"),
                new CustomerChargeLine("赔偿", "MD-1", CustomerChargeMethod.ADJUSTMENT, "",
                        null, null, null, null, new BigDecimal("20"), CustomerChargeDirection.DEDUCTION, "赔偿")));
        assertThat(snapshot.totalAmount()).isEqualByComparingTo("-10.00");
        assertThatThrownBy(() -> CustomerChargeCalculator.calculate(coverage("10", null), List.of(
                new CustomerChargeLine("加工费", "MD-1", CustomerChargeMethod.AGREED_AMOUNT, "",
                        null, null, null, null, new BigDecimal("1"), null, ""))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("议定金额必须保存约定说明");
    }

    private static CustomerChargeCoverage coverage(String shipped, BigDecimal loss) {
        return new CustomerChargeCoverage("FH-20261030-0001", new BigDecimal(shipped), "颗", loss);
    }

    private static CustomerChargeLine quantity(String name, String quantity, String price) {
        return new CustomerChargeLine(name, "MD-1", CustomerChargeMethod.QUANTITY, "颗",
                new BigDecimal(quantity), new BigDecimal(price), null, null, null, null, "");
    }

    private static CustomerChargeLine adjustment(String name, String quantity, String price,
                                                 CustomerChargeDirection direction, String basis) {
        return new CustomerChargeLine(name, "MD-1", CustomerChargeMethod.ADJUSTMENT, "颗",
                new BigDecimal(quantity), new BigDecimal(price), null, null, null, direction, basis);
    }
}
