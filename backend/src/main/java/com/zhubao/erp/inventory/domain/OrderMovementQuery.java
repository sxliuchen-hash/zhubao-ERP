package com.zhubao.erp.inventory.domain;

/**
 * 判断一张母单或子单是否已经留下库存流水。
 * 母单看母单号下的任意流水；子单只认自己的单据号或工单号，不把同母单的其他子单算进来。
 */
public final class OrderMovementQuery {

    private OrderMovementQuery() {
    }

    public static boolean matches(StockLedgerEntry entry, String motherOrderCode, String orderCode) {
        if (entry == null || entry.to() == null || motherOrderCode == null || orderCode == null
                || !motherOrderCode.equals(entry.to().motherOrderCode())) {
            return false;
        }
        if (motherOrderCode.equals(orderCode)) {
            return true;
        }
        if (orderCode.equals(entry.documentCode()) || orderCode.equals(entry.to().workOrderCode())) {
            return true;
        }
        return entry.from() != null && orderCode.equals(entry.from().workOrderCode());
    }
}
