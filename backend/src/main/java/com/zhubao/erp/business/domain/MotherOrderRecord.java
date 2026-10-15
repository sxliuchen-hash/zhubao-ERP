package com.zhubao.erp.business.domain;

import java.math.BigDecimal;

public record MotherOrderRecord(
        String code,
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
        String ownerType,
        boolean customerOwned,
        String warehouseCode,
        BigDecimal quantity,
        String quantityUnit,
        BigDecimal weight,
        String stockStatus,
        String ledgerCode,
        String sourceRegisterType,
        Integer sourceRegisterLineNo,
        String sourceRegisterSequence,
        String operator,
        String evidenceCode,
        String remark,
        String status,
        String createdAt,
        String processingRequirement,
        String dueDate,
        String customerName
) {
    public MotherOrderRecord withSummary(BigDecimal nextQuantity, BigDecimal nextWeight) {
        return new MotherOrderRecord(code, customerId, ownerCustomerId, materialCode, inboundDate,
                expressCompany, courierNo, senderName, receiverName, unpackSigner, goodsName, ownerType,
                customerOwned, warehouseCode, nextQuantity, quantityUnit, nextWeight, stockStatus, ledgerCode,
                sourceRegisterType, sourceRegisterLineNo, sourceRegisterSequence, operator, evidenceCode,
                remark, status, createdAt, processingRequirement, dueDate, customerName);
    }
}
