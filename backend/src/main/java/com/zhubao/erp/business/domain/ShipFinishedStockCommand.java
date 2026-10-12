package com.zhubao.erp.business.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ShipFinishedStockCommand(
        @NotBlank String shipmentCode,
        @NotBlank String motherOrderCode,
        @NotBlank String workOrderCode,
        @NotNull Long customerId,
        Long ownerCustomerId,
        @NotBlank String materialCode,
        @NotBlank String fromWarehouseCode,
        @NotBlank String toWarehouseCode,
        String deliveryDate,
        String deliveryCategory,
        @NotNull @DecimalMin(value = "0.000", inclusive = false) BigDecimal quantity,
        String quantityUnit,
        @NotNull @DecimalMin(value = "0.000", inclusive = false) BigDecimal weight,
        String expressCompany,
        @NotBlank String logisticsNo,
        String paperExpressText,
        String sourceRegisterType,
        Integer sourceRegisterLineNo,
        String sourceRegisterSequence,
        String handler,
        String operator,
        String evidenceCode,
        String remark,
        String requestId
) {
    /** 旧调用不带请求编号。这种调用不回放，也不进入业务工作单元。 */
    public ShipFinishedStockCommand(
            String shipmentCode,
            String motherOrderCode,
            String workOrderCode,
            Long customerId,
            Long ownerCustomerId,
            String materialCode,
            String fromWarehouseCode,
            String toWarehouseCode,
            String deliveryDate,
            String deliveryCategory,
            BigDecimal quantity,
            String quantityUnit,
            BigDecimal weight,
            String expressCompany,
            String logisticsNo,
            String paperExpressText,
            String sourceRegisterType,
            Integer sourceRegisterLineNo,
            String sourceRegisterSequence,
            String handler,
            String operator,
            String evidenceCode,
            String remark
    ) {
        this(shipmentCode, motherOrderCode, workOrderCode, customerId, ownerCustomerId, materialCode,
                fromWarehouseCode, toWarehouseCode, deliveryDate, deliveryCategory, quantity, quantityUnit, weight,
                expressCompany, logisticsNo, paperExpressText, sourceRegisterType, sourceRegisterLineNo,
                sourceRegisterSequence, handler, operator, evidenceCode, remark, null);
    }
}
