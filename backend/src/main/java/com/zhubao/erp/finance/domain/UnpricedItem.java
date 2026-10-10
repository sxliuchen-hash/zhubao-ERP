package com.zhubao.erp.finance.domain;

/**
 * 还不能入账的单据。车间作业不因此中断。
 */
public record UnpricedItem(
        String yearMonth,
        Long customerId,
        String customerName,
        String motherOrderCode,
        String workOrderCode,
        String processName,
        String reason
) {
    public UnpricedItem {
        customerName = customerName == null ? "" : customerName;
        motherOrderCode = motherOrderCode == null ? "" : motherOrderCode;
        workOrderCode = workOrderCode == null ? "" : workOrderCode;
        processName = processName == null ? "" : processName;
    }
}
