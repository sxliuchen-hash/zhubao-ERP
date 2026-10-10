package com.zhubao.erp.finance.domain;

/**
 * 一个月的经营结果。当前自然月只代表截至本次查询，不拿它和完整月算增幅。
 */
public record FinanceTrendPoint(
        String yearMonth,
        boolean throughQueryTime,
        OperatingStatement operating
) {
}
