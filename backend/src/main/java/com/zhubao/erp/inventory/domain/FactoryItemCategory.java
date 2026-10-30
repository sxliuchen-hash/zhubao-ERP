package com.zhubao.erp.inventory.domain;

/**
 * 本厂物品的管理类别。只区分已确认的四类，不扩展成通用分类引擎。
 */
public enum FactoryItemCategory {
    /** 公司珠料。领用后仍按实际耗用计成本。 */
    BEAD,
    /** 公司配件。领用后仍按实际耗用计成本。 */
    ACCESSORY,
    /** 消耗品。计耗时点未选定时保持未定。 */
    CONSUMABLE,
    /** 可重复使用的工具。借用只改保管。 */
    REUSABLE_TOOL
}
