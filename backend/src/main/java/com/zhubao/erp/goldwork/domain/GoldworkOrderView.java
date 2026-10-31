package com.zhubao.erp.goldwork.domain;

import java.util.List;

/** 金工单查询。不返回件数、重量、成色或金额，避免把未核定计量写成 0。 */
public record GoldworkOrderView(
        String goldworkCode,
        String businessReference,
        long customerId,
        String customerName,
        String materialIntent,
        String materialIntentLabel,
        String requirement,
        String ownerName,
        List<String> routeStepIds,
        List<String> routeStepLabels,
        String status,
        String statusLabel,
        long version,
        String openedBy,
        String openedOn
) {
}
