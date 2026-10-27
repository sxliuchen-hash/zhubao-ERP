package com.zhubao.erp.business.domain;

/** 已生效的改单不能退回草稿，错误通过下一次更正改单处理。 */
public enum OrderChangeStatus {
    DRAFT,
    PENDING_FINANCE,
    FINANCE_CONFIRMED,
    DEFERRED_TO_SETTLEMENT,
    APPLIED,
    REJECTED,
    CANCELED
}
