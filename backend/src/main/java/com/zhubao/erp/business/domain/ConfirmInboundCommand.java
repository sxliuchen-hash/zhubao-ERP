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
        String inboundDate,
        String expressCompany,
        String courierNo,
        String senderName,
        String receiverName,
        String unpackSigner,
        String goodsName,
        String sourceRegisterType,
        Integer sourceRegisterLineNo,
        String sourceRegisterSequence,
        @NotBlank String warehouseCode,
        @NotNull @DecimalMin(value = "0.000", inclusive = false) BigDecimal quantity,
        String quantityUnit,
        @NotNull @DecimalMin(value = "0.000", inclusive = false) BigDecimal weight,
        String operator,
        String evidenceCode,
        String remark,
        @NotBlank(message = "加工要求不能为空") String processingRequirement,
        @NotBlank(message = "完成期限不能为空") String dueDate,
        String customerName,
        String requestId
) {
    public ConfirmInboundCommand(
            String motherOrderCode,
            Long customerId,
            Long ownerCustomerId,
            String materialCode,
            String inboundDate,
            String expressCompany,
            String courierNo,
            String senderName,
            String receiverName,
            String unpackSigner,
            String goodsName,
            String sourceRegisterType,
            Integer sourceRegisterLineNo,
            String sourceRegisterSequence,
            String warehouseCode,
            BigDecimal quantity,
            String quantityUnit,
            BigDecimal weight,
            String operator,
            String evidenceCode,
            String remark,
            String processingRequirement,
            String dueDate,
            String customerName
    ) {
        this(motherOrderCode, customerId, ownerCustomerId, materialCode, inboundDate, expressCompany, courierNo,
                senderName, receiverName, unpackSigner, goodsName, sourceRegisterType, sourceRegisterLineNo,
                sourceRegisterSequence, warehouseCode, quantity, quantityUnit, weight, operator, evidenceCode, remark,
                processingRequirement, dueDate, customerName, null);
    }
}
