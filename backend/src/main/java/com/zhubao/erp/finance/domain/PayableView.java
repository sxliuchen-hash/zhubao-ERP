package com.zhubao.erp.finance.domain;

import java.math.BigDecimal;

/**
 * 应付清单行，带出尚未支付的余额。
 */
public record PayableView(
        String code,
        String kind,
        String yearMonth,
        String payee,
        String sourceCode,
        Long customerId,
        String motherOrderCode,
        BigDecimal amount,
        BigDecimal paidAmount,
        BigDecimal unpaidAmount
) {
}
