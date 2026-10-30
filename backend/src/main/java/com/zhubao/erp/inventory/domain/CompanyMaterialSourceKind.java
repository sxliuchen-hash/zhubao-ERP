package com.zhubao.erp.inventory.domain;

/** 公司料入库的金额来源。期初和已有应付都不是一笔新采购。 */
public enum CompanyMaterialSourceKind {
    /** 本次采购入库，金额明确时才形成应付。 */
    PURCHASE,
    /** 期初结存。不生成采购，也不生成应付。 */
    OPENING,
    /** 关联已经存在的应付，不再开一笔。 */
    COVERED_PAYABLE
}
