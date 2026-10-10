package com.zhubao.erp.finance.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 单个客户的实物结存和加工费账单。实物取当前库存，不按月份回放。
 */
public record CustomerStatement(
        long customerId,
        String customerName,
        String yearMonth,
        BigDecimal stockQuantity,
        BigDecimal stockWeight,
        BigDecimal receivableAmount,
        BigDecimal receivedAmount,
        BigDecimal outstandingAmount,
        BigDecimal prepaymentAmount,
        List<ReceivableLine> lines,
        List<CustomerReceipt> receipts,
        String stockNote
) {
    public CustomerStatement {
        lines = List.copyOf(lines);
        receipts = List.copyOf(receipts);
    }
}
