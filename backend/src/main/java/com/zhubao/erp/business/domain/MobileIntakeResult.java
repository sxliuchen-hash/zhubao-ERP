package com.zhubao.erp.business.domain;

import java.math.BigDecimal;

public record MobileIntakeResult(
        boolean alreadyExists,
        String motherOrderCode,
        String courierNo,
        String customerName,
        String processingRequirement,
        String dueDate,
        String goodsName,
        BigDecimal quantity,
        BigDecimal weight,
        String ledgerCode,
        String qrContent
) {
}
