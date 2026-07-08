package com.zhubao.erp.business.domain;

import java.math.BigDecimal;

public record OutboundOrderRecord(
        String code,
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
        String handler,
        String sourceRegisterType,
        Integer sourceRegisterLineNo,
        String sourceRegisterSequence,
        String operator,
        String evidenceCode,
        String remark,
        String status,
        String ledgerCode,
        String createdAt
) {
}
