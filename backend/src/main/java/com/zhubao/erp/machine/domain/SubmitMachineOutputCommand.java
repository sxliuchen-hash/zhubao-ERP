package com.zhubao.erp.machine.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SubmitMachineOutputCommand(
        String code,
        @NotBlank String workDate,
        Long customerId,
        String customerNameSnapshot,
        @NotBlank String productCategory,
        Long machineId,
        @NotBlank String machineNoSnapshot,
        String workOrderCode,
        Long workProcessId,
        String workReportCode,
        @NotNull @DecimalMin(value = "0.000", inclusive = false) BigDecimal outputQuantity,
        String quantityUnit,
        Long operatorEmployeeId,
        @NotBlank String operatorSignatureName,
        Long signatureMediaId,
        String sourceRegisterType,
        Integer sourceLineNo,
        String machineLineNo,
        String sourceSequenceNo,
        String importBatchNo,
        String ledgerCode,
        String status,
        String remark,
        String operator
) {
}
