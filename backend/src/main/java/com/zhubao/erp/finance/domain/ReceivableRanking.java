package com.zhubao.erp.finance.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 当前客户未收款排行。没有未收时 rows 为空；无权时不返回空列表冒充已查过。
 */
public record ReceivableRanking(
        int total,
        int returned,
        boolean truncated,
        List<ReceivableRankRow> rows
) {
    public ReceivableRanking {
        rows = List.copyOf(rows);
    }

    public record ReceivableRankRow(
            long customerId,
            String customerName,
            BigDecimal outstandingAmount
    ) {
    }
}
