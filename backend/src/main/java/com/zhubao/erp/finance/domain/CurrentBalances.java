package com.zhubao.erp.finance.domain;

/**
 * 当前余额占位。本段不计算余额，序列化保持 null。
 */
public record CurrentBalances(
        String asOf,
        String outstandingAmount,
        String unpaidAmount
) {
}
