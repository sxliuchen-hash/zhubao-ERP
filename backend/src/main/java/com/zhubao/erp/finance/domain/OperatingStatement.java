package com.zhubao.erp.finance.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 单月经营结果。收入按应收入账月份计算，收款不重复算收入。
 */
public record OperatingStatement(
        String yearMonth,
        Long customerId,
        BigDecimal revenue,
        BigDecimal wageCost,
        BigDecimal consumableCost,
        BigDecimal depreciationCost,
        BigDecimal outsourceCost,
        BigDecimal result,
        List<String> gaps
) {
    public OperatingStatement {
        gaps = List.copyOf(gaps);
    }
}
