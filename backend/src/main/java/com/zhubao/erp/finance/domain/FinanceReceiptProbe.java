package com.zhubao.erp.finance.domain;

/**
 * 收款事务中的失败注入点。生产路径使用 {@link #NONE}。
 */
public interface FinanceReceiptProbe {

    FinanceReceiptProbe NONE = () -> {
    };

    void beforeAllocationInsert();
}
