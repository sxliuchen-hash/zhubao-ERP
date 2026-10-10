package com.zhubao.erp.finance.domain;

import java.util.List;

/**
 * 只读经营汇总。当前余额和客户排行在本段明确为空，不能用 0 或空数组代替。
 */
public record FinanceDashboard(
        String yearMonth,
        String generatedAt,
        FinanceDashboardScope scope,
        OperatingStatement operating,
        List<FinanceTrendPoint> trend,
        CurrentBalances currentBalances,
        ReceivableRanking receivableRanking,
        List<FinanceCapability> capabilities
) {
    public FinanceDashboard {
        trend = List.copyOf(trend);
        capabilities = List.copyOf(capabilities);
    }
}
