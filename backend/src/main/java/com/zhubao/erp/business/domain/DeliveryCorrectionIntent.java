package com.zhubao.erp.business.domain;

/**
 * 交付更正想做的事。交接规则未核定前，不能把其中的取消当成已经允许的出库冲回。
 */
public enum DeliveryCorrectionIntent {
    ABANDON_DRAFT,
    REVERSE_BEFORE_HANDOVER,
    OVERWRITE_QUANTITY,
    CANCEL_AND_REOPEN
}
