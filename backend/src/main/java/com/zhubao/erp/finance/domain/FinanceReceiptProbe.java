package com.zhubao.erp.finance.domain;

/**
 * 收款事务中的失败注入点。生产路径使用 {@link #NONE}。
 */
public interface FinanceReceiptProbe {

    FinanceReceiptProbe NONE = () -> {
    };

    void beforeAllocationInsert();

    /** 幂等查询之后、余额加锁之前。测试用来插入另一笔已提交核销。 */
    default void afterIdempotencyRead() {
    }
}
