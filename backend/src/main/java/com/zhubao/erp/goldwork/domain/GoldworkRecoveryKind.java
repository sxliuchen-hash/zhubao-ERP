package com.zhubao.erp.goldwork.domain;

/** 回收去向。成品仍留在产出事实里，这里不把回收并进去。 */
public enum GoldworkRecoveryKind {
    RECOVERABLE,
    REMAINDER,
    PHYSICAL_SCRAP,
    IRRECOVERABLE
}
