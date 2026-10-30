package com.zhubao.erp.finance.domain;

/**
 * 补收或扣减的方向。扣减是金额方向，不是再减一次实物损耗。
 */
public enum CustomerChargeDirection {
    SURCHARGE,
    DEDUCTION
}
