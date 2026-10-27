package com.zhubao.erp.finance.domain;

/** 结单版本状态。确认后的版本不可改写，调整另起版本。 */
public enum SettlementStatus {
    DRAFT,
    CONFIRMED
}
