package com.zhubao.erp.finance.domain;

import java.util.List;

/** 已有收款上的后续核销或撤销。不新开收款单，也不改写原收款金额。 */
public record ReceiptAllocationOperation(
        String code,
        String receiptCode,
        long customerId,
        String action,
        String reason,
        String operator,
        String requestId,
        String createdAt,
        List<ReceiptAllocation> lines
) {
    public static final String ALLOCATE = "ALLOCATE";
    public static final String REVOKE = "REVOKE";

    public ReceiptAllocationOperation {
        lines = lines == null ? List.of() : List.copyOf(lines);
        reason = reason == null ? "" : reason;
        action = action == null ? "" : action;
    }
}
