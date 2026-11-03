package com.zhubao.erp.business.domain;

/**
 * 交付单状态。草稿只记录来源，不表示已经出库。
 */
public enum DeliveryDraftStatus {
    DRAFT,
    CONFIRMED,
    ABANDONED,
    /** 未交接前取消出库。原出库记录保留，库存追加冲回。 */
    REVERSED
}
