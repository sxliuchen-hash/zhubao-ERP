package com.zhubao.erp.finance.domain;

import java.math.BigDecimal;

/**
 * 加工费应收行。金额只追加，冲销用负数红字并指回原行。
 */
public record ReceivableLine(
        String code,
        long customerId,
        String customerName,
        String yearMonth,
        String sourceType,
        String sourceKey,
        String motherOrderCode,
        String workOrderCode,
        String processName,
        BigDecimal quantity,
        ChargeUnit unit,
        BigDecimal unitPrice,
        BigDecimal amount,
        String reversesCode,
        String remark,
        String operator,
        String createdAt
) {
    public ReceivableLine {
        workOrderCode = workOrderCode == null ? "" : workOrderCode;
        processName = processName == null ? "" : processName;
        reversesCode = reversesCode == null ? "" : reversesCode;
        remark = remark == null ? "" : remark;
    }
}
