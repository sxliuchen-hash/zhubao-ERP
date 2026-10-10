package com.zhubao.erp.finance.domain;

/**
 * 付款事务中的失败注入点。生产路径使用 {@link #NONE}。
 */
public interface FinancePaymentProbe {

    FinancePaymentProbe NONE = new FinancePaymentProbe() {
    };

    default void beforePaymentInsert() {
    }

    default void beforeBalanceUpdate() {
    }
}
