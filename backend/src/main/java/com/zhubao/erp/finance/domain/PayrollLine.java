package com.zhubao.erp.finance.domain;

import java.math.BigDecimal;

/**
 * 一道已完成工序的工资行。确认后锁定，不生成银行代发。
 */
public record PayrollLine(
        String code,
        String yearMonth,
        String operatorName,
        String orderCode,
        String processName,
        String motherOrderCode,
        long customerId,
        BigDecimal quantity,
        ChargeUnit unit,
        BigDecimal unitPrice,
        BigDecimal grossAmount,
        BigDecimal lossDeduction,
        BigDecimal netAmount,
        boolean confirmed,
        String sourceKey,
        String sourceMode,
        String payeeAccount
) {
    public static final String LEGACY = "LEGACY";
    public static final String POST_DELIVERY = "POST_DELIVERY";

    public PayrollLine(
            String code,
            String yearMonth,
            String operatorName,
            String orderCode,
            String processName,
            String motherOrderCode,
            long customerId,
            BigDecimal quantity,
            ChargeUnit unit,
            BigDecimal unitPrice,
            BigDecimal grossAmount,
            BigDecimal lossDeduction,
            BigDecimal netAmount,
            boolean confirmed,
            String sourceKey
    ) {
        this(code, yearMonth, operatorName, orderCode, processName, motherOrderCode, customerId, quantity, unit,
                unitPrice, grossAmount, lossDeduction, netAmount, confirmed, sourceKey, LEGACY, "");
    }

    public PayrollLine {
        sourceMode = sourceMode == null || sourceMode.isBlank() ? LEGACY : sourceMode;
        payeeAccount = payeeAccount == null ? "" : payeeAccount;
    }

    public PayrollLine confirm() {
        return new PayrollLine(
                code, yearMonth, operatorName, orderCode, processName, motherOrderCode, customerId, quantity, unit,
                unitPrice, grossAmount, lossDeduction, netAmount, true, sourceKey, sourceMode, payeeAccount
        );
    }
}
