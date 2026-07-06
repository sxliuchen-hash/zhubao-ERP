package com.zhubao.erp.business.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ApproveLossCommand(
        @NotBlank String approvalCode,
        @NotBlank String motherOrderCode,
        @NotBlank String workOrderCode,
        @NotNull Long customerId,
        Long ownerCustomerId,
        @NotBlank String materialCode,
        @NotBlank String process,
        @NotBlank String operator,
        @NotBlank String approver,
        @NotNull @DecimalMin(value = "0.000", inclusive = false) BigDecimal inputWeight,
        @NotNull @DecimalMin(value = "0.000") BigDecimal outputWeight,
        @NotNull @DecimalMin(value = "0.000") BigDecimal reasonableTransferWeight,
        @NotNull @DecimalMin(value = "0.000") BigDecimal standardLossRatePercent,
        @NotNull @DecimalMin(value = "0.000") BigDecimal acceptedQuantity,
        @NotNull @DecimalMin(value = "0.000") BigDecimal pieceRate,
        @NotNull @DecimalMin(value = "0.000") BigDecimal lossDeductionUnitPrice,
        String evidenceCode,
        String remark
) {
}
