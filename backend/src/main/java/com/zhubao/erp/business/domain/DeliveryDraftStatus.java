package com.zhubao.erp.business.domain;

/**
 * 交付单状态。草稿只记录来源，不表示已经出库。
 */
public enum DeliveryDraftStatus {
    DRAFT,
    CONFIRMED,
    ABANDONED
}
