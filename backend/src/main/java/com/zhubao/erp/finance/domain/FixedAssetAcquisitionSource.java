package com.zhubao.erp.finance.domain;

/** 资产购置的金额来源。已有卡片和已有应付都不是一笔新采购。 */
public enum FixedAssetAcquisitionSource {
    /** 本次购置。金额明确时才形成卡片和未付应付。 */
    PURCHASE,
    /** 沿用已有卡片。不新建卡片，也不补一笔采购。 */
    EXISTING_CARD,
    /** 关联已经存在的应付，不再开一笔。 */
    COVERED_PAYABLE
}
