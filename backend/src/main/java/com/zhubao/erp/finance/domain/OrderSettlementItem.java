package com.zhubao.erp.finance.domain;

import java.math.BigDecimal;

/** 结单上的一行客户费或员工工费。金额可在草稿中暂缺，确认前必须补齐。 */
public record OrderSettlementItem(
        int lineNo,
        SettlementItemKind kind,
        String title,
        String sourceRef,
        String chargeMode,
        BigDecimal quantity,
        String quantityUnit,
        String chargeUnit,
        BigDecimal unitPrice,
        BigDecimal grossAmount,
        BigDecimal deduction,
        BigDecimal netAmount,
        String payeeAccount,
        String payeeName,
        String remark,
        String lossSourceCode
) {
    public OrderSettlementItem {
        title = title == null ? "" : title;
        sourceRef = sourceRef == null ? "" : sourceRef;
        chargeMode = chargeMode == null ? "" : chargeMode;
        quantityUnit = quantityUnit == null ? "" : quantityUnit;
        chargeUnit = chargeUnit == null ? "" : chargeUnit;
        deduction = deduction == null ? BigDecimal.ZERO : deduction;
        payeeAccount = payeeAccount == null ? "" : payeeAccount;
        payeeName = payeeName == null ? "" : payeeName;
        remark = remark == null ? "" : remark;
        lossSourceCode = lossSourceCode == null ? "" : lossSourceCode;
    }
}
