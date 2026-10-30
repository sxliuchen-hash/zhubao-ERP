package com.zhubao.erp.inventory.domain;

/**
 * 计耗时点。未核定的物品保持未定，不能当成领用即耗或已发生的成本。
 */
public enum ConsumptionTiming {
    /** 实际品名的计耗方式尚未选定。 */
    UNDECIDED,
    /** 领用当时计耗。只在耗材明确选择后使用。 */
    ON_ISSUE,
    /** 领用只移位，实际耗用才计成本。 */
    ON_ACTUAL_USE
}
