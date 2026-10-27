package com.zhubao.erp.finance.domain;

import java.math.BigDecimal;

/**
 * 一张订单的结单版本。合计是当时凭据，不是另一套可手改的客户余额。
 */
public record OrderSettlement(
        String code,
        String rootCode,
        int versionNo,
        String previousCode,
        String targetType,
        String targetCode,
        String motherOrderCode,
        long customerId,
        String customerName,
        SettlementStatus status,
        int rowVersion,
        String deliveryRevision,
        BigDecimal customerNet,
        BigDecimal employeeNet,
        boolean customerWaived,
        String waiveReason,
        boolean noInternalLabor,
        String noLaborReason,
        String preparer,
        String confirmedBy,
        String createdAt,
        String confirmedAt,
        String postingMonth,
        String legacyState
) {
    public OrderSettlement {
        previousCode = previousCode == null ? "" : previousCode;
        waiveReason = waiveReason == null ? "" : waiveReason;
        noLaborReason = noLaborReason == null ? "" : noLaborReason;
        preparer = preparer == null ? "" : preparer;
        confirmedBy = confirmedBy == null ? "" : confirmedBy;
        confirmedAt = confirmedAt == null ? "" : confirmedAt;
        postingMonth = postingMonth == null ? "" : postingMonth;
        legacyState = legacyState == null || legacyState.isBlank() ? "NONE" : legacyState;
        customerName = customerName == null ? "" : customerName;
    }
}
