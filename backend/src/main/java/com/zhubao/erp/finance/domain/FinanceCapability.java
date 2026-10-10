package com.zhubao.erp.finance.domain;

/** 仪表盘某一块是否已经提供。不可用必须写原因，不能用金额 0 表示。 */
public record FinanceCapability(
        String part,
        boolean available,
        String reason
) {
}
