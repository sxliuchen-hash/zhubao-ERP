package com.zhubao.erp.finance.domain;

/** 维修进度。已登记还没有费用；确认后才有唯一费用来源。 */
public enum FixedAssetRepairState {
    /** 已登记，费用未确认。 */
    RECORDED,
    /** 费用已确认，对应一笔应付。 */
    CONFIRMED
}
