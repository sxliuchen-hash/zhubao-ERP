package com.zhubao.erp.finance.domain;

import java.math.BigDecimal;

/**
 * 应付付款。不能超过尚未支付的金额。
 */
public record PayablePayment(
        String code,
        String payableCode,
        BigDecimal amount,
        String paidOn,
        String operator,
        String createdAt
) {
}
