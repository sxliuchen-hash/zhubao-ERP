package com.zhubao.erp.business.app;

/** 单据维护列表中的一张未回收母单，不含改正明细和入账判断。 */
public record MaintainedMotherSummary(
        String code,
        String customerName,
        String operator,
        String orderDate,
        String goodsName,
        String materialCode,
        String quantity,
        String weight
) {
}
