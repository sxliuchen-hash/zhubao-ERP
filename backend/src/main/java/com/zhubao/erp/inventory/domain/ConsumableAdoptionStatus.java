package com.zhubao.erp.inventory.domain;

/** 旧耗材接续结果。待核时不另开可改余额。 */
public enum ConsumableAdoptionStatus {
    /** 已接到唯一数量账，旧数量不能再写。 */
    ADOPTED,
    /** 来源不清，只列待核。 */
    PENDING
}
