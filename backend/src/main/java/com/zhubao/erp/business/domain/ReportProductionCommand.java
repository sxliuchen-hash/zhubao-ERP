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
        @NotNull @DecimalMin(value = "0.000", inclusive = false) BigDecimal weight,
        @NotBlank String process,
        @NotBlank String operator,
        String handoffCode,
        String remark
) {
}
