package com.zhubao.erp.finance.domain;

import java.math.BigDecimal;

/** 结单明细到应收、工资或应付的入账关系。 */
public record OrderSettlementPosting(
        int itemLineNo,
        String ledgerKind,
        String ledgerCode,
        BigDecimal amount
) {
    public static final String RECEIVABLE = "RECEIVABLE";
    public static final String PAYROLL = "PAYROLL";
    public static final String PAYABLE = "PAYABLE";

    public OrderSettlementPosting {
        ledgerKind = ledgerKind == null ? "" : ledgerKind;
        ledgerCode = ledgerCode == null ? "" : ledgerCode;
    }
}
