package com.zhubao.erp.business.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CustomerReturnCommand(
        @NotBlank String returnCode,
        @NotBlank String originalShipmentCode,
        @NotBlank String motherOrderCode,
        @NotBlank String workOrderCode,
        @NotNull Long customerId,
        Long ownerCustomerId,
        @NotBlank String materialCode,
        @NotBlank String fromWarehouseCode,
        @NotBlank String toWarehouseCode,
        @NotNull @DecimalMin(value = "0.000", inclusive = false) BigDecimal quantity,
        @NotNull @DecimalMin(value = "0.000", inclusive = false) BigDecimal weight,
        @NotBlank String returnLogisticsNo,
        @NotBlank String reason,
        String operator,
        String evidenceCode,
        String remark,
        String requestId
) {
    public CustomerReturnCommand(
            String returnCode,
            String originalShipmentCode,
            String motherOrderCode,
            String workOrderCode,
            Long customerId,
            Long ownerCustomerId,
            String materialCode,
            String fromWarehouseCode,
            String toWarehouseCode,
            BigDecimal quantity,
            BigDecimal weight,
            String returnLogisticsNo,
            String reason,
            String operator,
            String evidenceCode,
            String remark
    ) {
        this(returnCode, originalShipmentCode, motherOrderCode, workOrderCode, customerId, ownerCustomerId,
                materialCode, fromWarehouseCode, toWarehouseCode, quantity, weight, returnLogisticsNo, reason,
                operator, evidenceCode, remark, "");
    }
}
