package com.zhubao.erp.finance.domain;

import java.util.List;

/** 交货后结单草稿或确认。金额以服务端重算为准。 */
public record SettlementCommand(
        String requestId,
        String targetType,
        String targetCode,
        int expectedVersion,
        String deliveryRevision,
        boolean customerWaived,
        String waiveReason,
        boolean noInternalLabor,
        String noLaborReason,
        List<OrderSettlementItem> items
) {
    public SettlementCommand {
        items = items == null ? List.of() : List.copyOf(items);
        waiveReason = waiveReason == null ? "" : waiveReason.trim();
        noLaborReason = noLaborReason == null ? "" : noLaborReason.trim();
        deliveryRevision = deliveryRevision == null ? "" : deliveryRevision.trim();
    }
}
