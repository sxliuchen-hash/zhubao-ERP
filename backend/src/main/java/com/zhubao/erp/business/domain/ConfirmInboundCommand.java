package com.zhubao.erp.business.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ConfirmInboundCommand(
        @NotBlank String motherOrderCode,
        @NotNull Long customerId,
        Long ownerCustomerId,
        @NotBlank String materialCode,
        @NotBlank String warehouseCode,
        @NotNull @DecimalMin(value = "0.000", inclusive = false) BigDecimal quantity,
        @NotNull @DecimalMin(value = "0.000", inclusive = false) BigDecimal weight,
        @NotBlank String operator,
        String evidenceCode,
        String remark
) {
}
