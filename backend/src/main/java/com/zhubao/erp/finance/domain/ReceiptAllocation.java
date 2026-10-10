package com.zhubao.erp.finance.domain;

import java.math.BigDecimal;

/**
 * 收款核销到某一条应收。
 */
public record ReceiptAllocation(
        String receivableCode,
        BigDecimal amount
) {
}
