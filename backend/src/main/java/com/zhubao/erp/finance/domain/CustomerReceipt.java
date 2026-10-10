package com.zhubao.erp.finance.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 客户收款。未核销余额记为预收，不重复计入加工费收入。
 */
public record CustomerReceipt(
        String code,
        long customerId,
        String customerName,
        String yearMonth,
        BigDecimal amount,
        String receivedOn,
        List<ReceiptAllocation> allocations,
        String operator,
        String createdAt
) {
    public CustomerReceipt {
        allocations = List.copyOf(allocations);
    }
}
