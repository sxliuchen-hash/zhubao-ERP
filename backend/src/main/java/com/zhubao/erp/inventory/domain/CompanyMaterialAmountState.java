package com.zhubao.erp.inventory.domain;

/** 入库金额是否已经明确。未明确不是 0。 */
public enum CompanyMaterialAmountState {
    /** 金额已经明确。 */
    KNOWN,
    /** 采购金额尚未明确。 */
    INCOMPLETE,
    /** 不是本次采购，不形成新的应付。 */
    NOT_PURCHASE
}
