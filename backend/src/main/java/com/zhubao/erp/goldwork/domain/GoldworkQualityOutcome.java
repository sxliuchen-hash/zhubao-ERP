package com.zhubao.erp.goldwork.domain;

/** 金工份额的放行、返工或待核。不套用珠料质检默认。 */
public enum GoldworkQualityOutcome {
    RELEASE,
    REWORK,
    PENDING
}
