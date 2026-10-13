package com.zhubao.erp.inventory.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StockDimension(
        @NotNull OwnerType ownerType,
        Long ownerCustomerId,
        Long customerId,
        String motherOrderCode,
        String workOrderCode,
        @NotBlank String materialCode,
        @NotBlank String warehouseCode,
        @NotNull StockStatus stockStatus,
        String batchCode
) {
    /** 既有加工库存不带批次，维度键保持原来的 8 段。 */
    public StockDimension(
            OwnerType ownerType,
            Long ownerCustomerId,
            Long customerId,
            String motherOrderCode,
            String workOrderCode,
            String materialCode,
            String warehouseCode,
            StockStatus stockStatus
    ) {
        this(ownerType, ownerCustomerId, customerId, motherOrderCode, workOrderCode, materialCode, warehouseCode, stockStatus, "");
    }

    public StockDimension {
        motherOrderCode = motherOrderCode == null ? "" : motherOrderCode.trim();
        batchCode = batchCode == null ? "" : batchCode.trim();
    }
}
