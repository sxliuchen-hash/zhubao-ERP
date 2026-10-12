package com.zhubao.erp.finance.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 经营指标的只读来源明细。totalAmount 是筛选后的全量合计，不是当前页相加。
 */
public record FinanceDetailPage(
        String kind,
        String asOf,
        String yearMonth,
        int page,
        int size,
        int total,
        BigDecimal totalAmount,
        List<FinanceDetailRow> rows
) {
    public FinanceDetailPage {
        rows = List.copyOf(rows);
    }

    public record FinanceDetailRow(
            String sourceType,
            String sourceCode,
            Long customerId,
            String customerName,
            String yearMonth,
            BigDecimal amount,
            String note
    ) {
    }
}
