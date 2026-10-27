package com.zhubao.erp.finance.domain;

import java.math.BigDecimal;

/**
 * 公司应付。种类为工资、消耗品采购或外协，不是银行账户。
 */
public record Payable(
        String code,
        String kind,
        String yearMonth,
        String payee,
        String sourceCode,
        Long customerId,
        String motherOrderCode,
        BigDecimal amount,
        BigDecimal paidAmount,
        String operator,
        String createdAt,
        String payeeAccount,
        String adjustsCode,
        String postingKey
) {
    public Payable {
        motherOrderCode = motherOrderCode == null ? "" : motherOrderCode;
        sourceCode = sourceCode == null ? "" : sourceCode;
        payeeAccount = payeeAccount == null ? "" : payeeAccount;
        adjustsCode = adjustsCode == null ? "" : adjustsCode;
    }

    public Payable(
            String code,
            String kind,
            String yearMonth,
            String payee,
            String sourceCode,
            Long customerId,
            String motherOrderCode,
            BigDecimal amount,
            BigDecimal paidAmount,
            String operator,
            String createdAt
    ) {
        this(code, kind, yearMonth, payee, sourceCode, customerId, motherOrderCode, amount, paidAmount, operator,
                createdAt, "", "", null);
    }

    public Payable pay(BigDecimal paidAmount) {
        return new Payable(
                code, kind, yearMonth, payee, sourceCode, customerId, motherOrderCode, amount, paidAmount, operator,
                createdAt, payeeAccount, adjustsCode, postingKey
        );
    }
}
