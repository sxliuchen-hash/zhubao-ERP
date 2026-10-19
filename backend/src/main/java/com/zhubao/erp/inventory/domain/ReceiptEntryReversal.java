package com.zhubao.erp.inventory.domain;

import java.math.BigDecimal;

/**
 * 收货录入错误的库存冲正。只扣回原收货去向的余额，并指回原流水。
 * 不改变客户、材质，也不把数量转到报废或冻结。单据事实和改单请求由后续步骤接入。
 */
public record ReceiptEntryReversal(
        String sourceLedgerCode,
        String motherOrderCode,
        StockDimension originalDestination,
        BigDecimal quantity,
        BigDecimal weight,
        String remark
) {
}
