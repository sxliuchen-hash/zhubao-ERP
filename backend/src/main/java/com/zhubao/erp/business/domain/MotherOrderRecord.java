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
        String createdAt
) {
}
