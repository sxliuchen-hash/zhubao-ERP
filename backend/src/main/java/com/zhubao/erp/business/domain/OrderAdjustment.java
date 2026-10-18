package com.zhubao.erp.business.domain;

/** 管理员改正单据时留下的前后内容。不改写已入账的库存和账单。 */
public record OrderAdjustment(
        String targetType,
        String code,
        String beforeText,
        String afterText,
        String reason,
        String operatorName,
        String createdAt
) {
}
