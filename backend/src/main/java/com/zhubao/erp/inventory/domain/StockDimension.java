package com.zhubao.erp.inventory.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StockDimension(
        @NotNull OwnerType ownerType,
        Long ownerCustomerId,
        Long customerId,
        @NotBlank String motherOrderCode,
        String workOrderCode,
        @NotBlank String materialCode,
        @NotBlank String warehouseCode,
        @NotNull StockStatus stockStatus
) {
}
