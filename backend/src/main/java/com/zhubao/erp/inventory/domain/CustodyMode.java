package com.zhubao.erp.inventory.domain;

/**
 * 工具保管方式。未确认按数量还是逐件时保持未定。
 */
public enum CustodyMode {
    /** 尚未确认按数量还是逐件。 */
    UNDECIDED,
    /** 同质工具按数量借还。 */
    QUANTITY,
    /** 需要追溯的工具逐件编号。 */
    PIECE
}
