package com.zhubao.erp.business.domain;

import java.math.BigDecimal;

/**
 * 同一母单上的中途改单。不覆盖已发生的交付、库存和账单。
 */
public record OrderChange(
        String id,
        String motherOrderCode,
        String workOrderCode,
        String oldRequirement,
        String newRequirement,
        String oldDueDate,
        String newDueDate,
        OrderChangeStatus status,
        int versionNo,
        boolean amountUnchanged,
        BigDecimal customerUnitPrice,
        BigDecimal workerFee,
        String financeNote,
        String confirmedBy,
        String requestId,
        String createdBy,
        String createdAt,
        String updatedAt
) {
}
