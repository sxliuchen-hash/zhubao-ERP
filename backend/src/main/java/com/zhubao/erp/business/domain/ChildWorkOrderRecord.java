package com.zhubao.erp.business.domain;

import java.math.BigDecimal;

/**
 * 从母单拆出的子单。抄下客户、加工要求和期限，并记下这一张要做的工序。
 */
public record ChildWorkOrderRecord(
        String code,
        String motherOrderCode,
        Long customerId,
        String customerName,
        String processingRequirement,
        String dueDate,
        String materialCode,
        String goodsName,
        BigDecimal quantity,
        String quantityUnit,
        BigDecimal weight,
        String warehouseCode,
        String processes,
        String verificationCode,
        String ledgerCode,
        String operator,
        String createdAt
) {
}
