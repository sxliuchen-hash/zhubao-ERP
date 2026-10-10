package com.zhubao.erp.finance.domain;

import java.util.List;

/**
 * 本次查询实际使用的范围。不回传未授权客户。
 */
public record FinanceDashboardScope(
        String kind,
        List<Long> customerIds,
        String note
) {
    public FinanceDashboardScope {
        customerIds = List.copyOf(customerIds);
    }
}
