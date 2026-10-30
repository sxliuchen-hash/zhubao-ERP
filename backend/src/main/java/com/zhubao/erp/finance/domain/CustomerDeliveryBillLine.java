package com.zhubao.erp.finance.domain;

import java.math.BigDecimal;

/**
 * 账单上保存的一行计算结果，供以后按同一快照复算核对。
 */
public record CustomerDeliveryBillLine(
        String billId,
        int lineNo,
        String itemName,
        CustomerChargeMethod method,
        BigDecimal amount,
        String expression
) {
}
