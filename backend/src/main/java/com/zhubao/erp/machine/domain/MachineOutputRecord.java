package com.zhubao.erp.machine.domain;

import java.math.BigDecimal;

public record MachineOutputRecord(
        String code,
        String workDate,
        Long customerId,
        String customerNameSnapshot,
        String productCategory,
        Long machineId,
        String machineNoSnapshot,
        String workOrderCode,
        Long workProcessId,
        String workReportCode,
        BigDecimal outputQuantity,
        String quantityUnit,
        Long operatorEmployeeId,
        String operatorSignatureName,
        Long signatureMediaId,
        String sourceRegisterType,
        Integer sourceLineNo,
        String machineLineNo,
        String sourceSequenceNo,
        String importBatchNo,
        String ledgerCode,
        String status,
        String remark,
        String createdBy,
        String createdAt
) {
}
