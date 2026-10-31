package com.zhubao.erp.finance.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 账单上保存的一行。金额和表达式只作核对，复算必须用同时保存的原输入。
 */
public record CustomerDeliveryBillLine(
        String billId,
        int lineNo,
        String itemName,
        CustomerChargeMethod method,
        BigDecimal amount,
        String expression,
        String targetCode,
        String quantityUnit,
        CustomerChargeDirection direction,
        BigDecimal inputQuantity,
        BigDecimal unitPrice,
        BigDecimal baseQuantity,
        BigDecimal exemptQuantity,
        BigDecimal directAmount,
        BigDecimal billQuantity,
        String basis,
        BigDecimal shippedQuantity,
        BigDecimal lossQuantity,
        String coverageUnit,
        Integer quantityScale,
        Integer priceScale,
        Integer moneyScale,
        String rounding,
        List<CustomerChargeSource> coverageSources
) {
    public CustomerDeliveryBillLine {
        coverageSources = coverageSources == null ? List.of() : List.copyOf(coverageSources);
    }

    public CustomerDeliveryBillLine(String billId, int lineNo, String itemName, CustomerChargeMethod method,
            BigDecimal amount, String expression, String targetCode, String quantityUnit, CustomerChargeDirection direction,
            BigDecimal inputQuantity, BigDecimal unitPrice, BigDecimal baseQuantity, BigDecimal exemptQuantity,
            BigDecimal directAmount, BigDecimal billQuantity, String basis, BigDecimal shippedQuantity,
            BigDecimal lossQuantity, String coverageUnit, Integer quantityScale, Integer priceScale, Integer moneyScale, String rounding) {
        this(billId, lineNo, itemName, method, amount, expression, targetCode, quantityUnit, direction,
                inputQuantity, unitPrice, baseQuantity, exemptQuantity, directAmount, billQuantity, basis,
                shippedQuantity, lossQuantity, coverageUnit, quantityScale, priceScale, moneyScale, rounding, List.of());
    }
    public static CustomerDeliveryBillLine capture(String billId, int lineNo, CustomerChargeLine input,
                                                   CustomerChargeSnapshot.CustomerChargeResultLine result,
                                                   CustomerChargeSnapshot snapshot) {
        CustomerChargeCoverage coverage = snapshot.coverage();
        return new CustomerDeliveryBillLine(
                billId, lineNo, result.name(), result.method(), result.amount(), result.expression(),
                input.targetCode(), input.quantityUnit(), input.direction(),
                input.quantity(), input.unitPrice(), input.baseQuantity(), input.exemptQuantity(), input.directAmount(),
                result.billQuantity(), input.basis(),
                coverage.shippedQuantity(), coverage.lossQuantity(), coverage.quantityUnit(),
                snapshot.quantityScale(), snapshot.priceScale(), snapshot.moneyScale(), snapshot.rounding(), coverage.sources());
    }

    public CustomerChargeLine chargeLine() {
        return new CustomerChargeLine(
                itemName, targetCode, method, quantityUnit,
                inputQuantity, unitPrice, baseQuantity, exemptQuantity, directAmount, direction, basis);
    }
}
