package com.zhubao.erp.finance.domain;

import java.math.BigDecimal;

/**
 * 截至本次查询的当前余额。不随所选月份改成该月账单小计。
 */
public record CurrentBalances(
        String asOf,
        BigDecimal outstandingAmount,
        BigDecimal unpaidAmount,
        String limitation
) {
}
