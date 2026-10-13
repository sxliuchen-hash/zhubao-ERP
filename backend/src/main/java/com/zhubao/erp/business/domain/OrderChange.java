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
        String updatedAt,
        BigDecimal customerDelta,
        BigDecimal workerDelta,
        String receivableCode,
        String processName,
        String workerName,
        String oldRoute,
        String newRoute,
        int expectedVersion,
        String reworkProcess
) {
    public OrderChange {
        customerDelta = customerDelta == null ? BigDecimal.ZERO : customerDelta;
        workerDelta = workerDelta == null ? BigDecimal.ZERO : workerDelta;
        receivableCode = receivableCode == null ? "" : receivableCode;
        processName = processName == null ? "" : processName;
        workerName = workerName == null ? "" : workerName;
        oldRoute = oldRoute == null ? "" : oldRoute;
        newRoute = newRoute == null ? "" : newRoute;
        reworkProcess = reworkProcess == null ? "" : reworkProcess;
    }

    /** 非财务查看时去掉单价、工费、差额和应收编号。 */
    public OrderChange withoutMoney() {
        return new OrderChange(
                id, motherOrderCode, workOrderCode, oldRequirement, newRequirement, oldDueDate, newDueDate,
                status, versionNo, amountUnchanged, BigDecimal.ZERO, BigDecimal.ZERO, financeNote, confirmedBy,
                requestId, createdBy, createdAt, updatedAt, BigDecimal.ZERO, BigDecimal.ZERO, "",
                processName, workerName, oldRoute, newRoute, expectedVersion, reworkProcess
        );
    }
}
