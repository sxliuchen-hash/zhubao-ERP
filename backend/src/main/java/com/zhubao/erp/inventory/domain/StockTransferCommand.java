package com.zhubao.erp.inventory.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record StockTransferCommand(
        @NotBlank String documentType,
        @NotBlank String documentCode,
        @Valid StockDimension from,
        @NotNull @Valid StockDimension to,
        @NotNull @DecimalMin(value = "0.000", inclusive = false) BigDecimal quantity,
        @NotNull @DecimalMin(value = "0.000", inclusive = false) BigDecimal weight,
        String remark
) {
}
