package com.zhubao.erp.finance.domain;

/**
 * 消耗品档案。数量和金额不进入客户金料库存。
 */
public record ConsumableItem(
        String code,
        String name,
        String unitName
) {
}
