package com.zhubao.erp.business.domain;

/** 袋上适用重量。未知和待复称都不是 0，也不能由颗数换算。 */
public enum BagWeightState {
    UNKNOWN,
    PENDING_REWEIGH,
    KNOWN
}
