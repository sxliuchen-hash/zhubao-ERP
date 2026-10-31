package com.zhubao.erp.finance.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 财务待办读模型。已收不计入收入，已付不计入成本。未齐时不给出把缺失成本当成 0 的收益。
 */
public record FinanceContinuityView(
        BigDecimal confirmedIncome,
        BigDecimal collected,
        BigDecimal uncollected,
        BigDecimal confirmedLaborCost,
        BigDecimal paid,
        BigDecimal unpaid,
        List<String> awaitingCustomerBill,
        List<String> awaitingEmployeeSettlement,
        boolean costIncomplete,
        BigDecimal recognizedProfit,
        List<String> awaitingReceiptCoverage
) {
}
