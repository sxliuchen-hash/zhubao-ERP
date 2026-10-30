package com.zhubao.erp.inventory.domain;

/**
 * 本厂物品档案。身份是类别、规格和单位；材质名称只用于显示，不能把不同规格并成一条。
 */
public record FactoryItem(
        String itemId,
        FactoryItemCategory category,
        String materialName,
        String specCode,
        String unit,
        ConsumptionTiming consumptionTiming,
        CustodyMode custodyMode,
        boolean enabled,
        int version
) {
}
