package com.zhubao.erp.business.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ReportProductionCommand(
        @NotBlank String motherOrderCode,
        @NotBlank String workOrderCode,
        @NotNull Long customerId,
        Long ownerCustomerId,
        @NotBlank String materialCode,
        @NotBlank String fromWarehouseCode,
        @NotBlank String toWarehouseCode,
        @NotNull @DecimalMin(value = "0.000", inclusive = false) BigDecimal quantity,
        String quantityUnit,
        @NotNull @DecimalMin(value = "0.000", inclusive = false) BigDecimal weight,
        String workDate,
        @NotBlank String process,
        String processCategory,
        String productCategory,
        String machineNo,
        String workItemName,
        String startedAt,
        String finishedAt,
        String completionStatus,
        String handoffAuditStatus,
        String sourceRegisterType,
        Integer sourceRegisterLineNo,
        String sourceRegisterSequence,
        @NotBlank String operator,
        String operatorSignatureName,
        String handoffCode,
        String remark
) {
}
